package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.LaundryServiceDAO;
import com.laundrylink.dao.OrderDAO;
import com.laundrylink.model.CustomerOption;
import com.laundrylink.model.LaundryService;
import com.laundrylink.model.OrderDetails;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.model.ServiceOption;
import com.laundrylink.util.SessionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laundry order rules shared by all modules.
 *
 * releaseOrder is the single release method from the development plan:
 * Status Tracking and the Dashboard both call it; do not duplicate the rule in the UI.
 *
 * Order rules (README "Business Rules"):
 *   - an order needs a customer and at least one service item;
 *   - quantities are greater than zero, whole numbers for per-piece services;
 *   - prices are copied into order_items so later price changes never alter an order;
 *   - customer and items change only while Received with no payments;
 *   - cancelling is limited to unpaid orders that have not started processing;
 *   - only the owner can delete an unpaid Received order entered by mistake.
 */
public class OrderService {

    public static final int MAX_NOTES_LENGTH = 500;

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999.99");
    private static final int ORDER_LIST_LIMIT = 2000;

    private final OrderDAO orderDAO = new OrderDAO();
    private final LaundryServiceDAO laundryServiceDAO = new LaundryServiceDAO();
    private final ServiceCatalogService serviceCatalogService = new ServiceCatalogService();

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public List<OrderSummary> loadOrders() throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return orderDAO.findAll(connection, ORDER_LIST_LIMIT);
        }
    }

    public OrderDetails loadOrder(int orderId) throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            OrderDetails details = orderDAO.findDetails(connection, orderId);
            if (details == null) {
                throw new ServiceException("That order no longer exists.");
            }
            return details;
        }
    }

    public List<CustomerOption> loadCustomers() throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return orderDAO.findCustomers(connection);
        }
    }

    /** Active services for the order form, read through the shared catalog contract. */
    public List<ServiceOption> loadActiveServices() throws ServiceException, SQLException {
        List<ServiceOption> options = new ArrayList<>();
        for (LaundryService service : serviceCatalogService.listActiveServices()) {
            options.add(ServiceOption.from(service));
        }
        return options;
    }

    // ------------------------------------------------------------------
    // Charge calculation (also used by the order form for live totals)
    // ------------------------------------------------------------------

    /** Item Subtotal = Quantity x Unit Price, rounded to centavos. */
    public static BigDecimal subtotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** Order Total = Sum of Item Subtotals. */
    public static BigDecimal total(List<OrderItemLine> items) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (OrderItemLine item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    /** Parses a typed weight or piece count and checks it against the service's pricing unit. */
    public static BigDecimal parseQuantity(String text, String pricingUnit) throws ServiceException {
        boolean perKilogram = ServiceOption.PER_KILOGRAM.equals(pricingUnit);
        String trimmed = text == null ? "" : text.trim().replace(",", "");
        if (trimmed.isEmpty()) {
            throw new ServiceException(perKilogram ? "Enter the weight in kilograms." : "Enter the number of pieces.");
        }
        BigDecimal quantity;
        try {
            quantity = new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new ServiceException(perKilogram
                    ? "Enter the weight as a number, for example 3.5."
                    : "Enter the number of pieces as a whole number, for example 2.");
        }
        validateQuantity(quantity, pricingUnit);
        return quantity;
    }

    public static void validateQuantity(BigDecimal quantity, String pricingUnit) throws ServiceException {
        boolean perKilogram = ServiceOption.PER_KILOGRAM.equals(pricingUnit);
        if (quantity == null || quantity.signum() <= 0) {
            throw new ServiceException(perKilogram
                    ? "Weight must be greater than zero."
                    : "Number of pieces must be greater than zero.");
        }
        int decimals = quantity.stripTrailingZeros().scale();
        if (!perKilogram && decimals > 0) {
            throw new ServiceException("Number of pieces must be a whole number.");
        }
        if (perKilogram && decimals > 2) {
            throw new ServiceException("Weight can have at most 2 decimal places.");
        }
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new ServiceException("Quantity is too large. The most allowed per item is "
                    + MAX_QUANTITY.toPlainString() + ".");
        }
    }

    // ------------------------------------------------------------------
    // Creating and editing
    // ------------------------------------------------------------------

    /** Creates an order received now by the logged-in user; returns the saved order. */
    public OrderDetails createOrder(int customerId, List<OrderItemLine> items, LocalDate expectedCompletionDate,
            String notes) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        String cleanNotes = validateOrderFields(customerId, items, expectedCompletionDate, notes,
                DashboardService.today());

        int orderId = TransactionHelper.inTransaction(connection -> {
            requireCustomer(connection, customerId);
            List<OrderItemLine> priced = priceItems(connection, items, new HashMap<Integer, OrderItemLine>());
            int id = orderDAO.insertOrder(connection, customerId, userId, expectedCompletionDate, cleanNotes);
            for (OrderItemLine item : priced) {
                orderDAO.insertItem(connection, id, item);
            }
            orderDAO.updateTotal(connection, id, total(priced));
            orderDAO.insertStatusHistory(connection, id, userId, OrderStatus.RECEIVED);
            return id;
        });
        return loadOrder(orderId);
    }

    /**
     * Replaces the customer, items, expected date and notes of an order that is
     * still Received with no payments. Lines already on the order keep their
     * saved price; newly added lines use the service's current price.
     */
    public OrderDetails updateOrder(int orderId, int customerId, List<OrderItemLine> items,
            LocalDate expectedCompletionDate, String notes) throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        TransactionHelper.inTransaction(connection -> {
            OrderDAO.LockedOrder order = requireOrder(connection, orderId);
            if (order.getStatus() != OrderStatus.RECEIVED) {
                throw new ServiceException("The customer and services can only be changed while the order is "
                        + "Received. This order is " + order.getStatus().getDisplayName() + ".");
            }
            if (order.hasPayments()) {
                throw new ServiceException("The customer and services cannot be changed after a payment "
                        + "has been recorded.");
            }
            String cleanNotes = validateOrderFields(customerId, items, expectedCompletionDate, notes,
                    order.getReceivedDate());
            requireCustomer(connection, customerId);

            Map<Integer, OrderItemLine> savedItems = new HashMap<>();
            for (OrderItemLine saved : orderDAO.findItems(connection, orderId)) {
                savedItems.put(saved.getItemId(), saved);
            }
            List<OrderItemLine> priced = priceItems(connection, items, savedItems);
            orderDAO.deleteItems(connection, orderId);
            for (OrderItemLine item : priced) {
                orderDAO.insertItem(connection, orderId, item);
            }
            orderDAO.updateOrder(connection, orderId, customerId, expectedCompletionDate, cleanNotes, total(priced));
            return null;
        });
        return loadOrder(orderId);
    }

    /** Updates only the expected completion date and notes; allowed for any open order. */
    public OrderDetails updateOrderSchedule(int orderId, LocalDate expectedCompletionDate, String notes)
            throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        TransactionHelper.inTransaction(connection -> {
            OrderDAO.LockedOrder order = requireOrder(connection, orderId);
            if (order.getStatus() == OrderStatus.RELEASED || order.getStatus() == OrderStatus.CANCELLED) {
                throw new ServiceException("This order is " + order.getStatus().getDisplayName()
                        + " and can no longer be changed.");
            }
            validateExpectedDate(expectedCompletionDate, order.getReceivedDate());
            orderDAO.updateSchedule(connection, orderId, expectedCompletionDate, cleanNotes(notes));
            return null;
        });
        return loadOrder(orderId);
    }

    // ------------------------------------------------------------------
    // Cancelling and deleting
    // ------------------------------------------------------------------

    /** Cancels an unpaid order that has not started processing; the record is kept. */
    public OrderDetails cancelOrder(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        TransactionHelper.inTransaction(connection -> {
            OrderDAO.LockedOrder order = requireOrder(connection, orderId);
            if (order.getStatus() != OrderStatus.RECEIVED) {
                throw new ServiceException("Only orders that have not started processing can be cancelled. "
                        + "This order is " + order.getStatus().getDisplayName() + ".");
            }
            if (order.hasPayments()) {
                throw new ServiceException("Orders with recorded payments cannot be cancelled.");
            }
            orderDAO.updateStatus(connection, orderId, OrderStatus.CANCELLED);
            orderDAO.insertStatusHistory(connection, orderId, userId, OrderStatus.CANCELLED);
            return null;
        });
        return loadOrder(orderId);
    }

    /** Owner only: permanently removes an unpaid Received order entered by mistake. */
    public void deleteOrder(int orderId) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        TransactionHelper.inTransaction(connection -> {
            OrderDAO.LockedOrder order = requireOrder(connection, orderId);
            if (order.getStatus() != OrderStatus.RECEIVED || order.hasPayments()) {
                throw new ServiceException("Only unpaid orders that are still Received can be deleted. "
                        + "Cancel the order instead to keep its record.");
            }
            orderDAO.deleteOrder(connection, orderId);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    /**
     * Moves an order one stage forward (Received, Washing, Drying, Ready for
     * Pickup) and records who did it. Shared with Order Status Tracking;
     * Ready for Pickup -> Released goes through releaseOrder instead.
     */
    public OrderDetails advanceStatus(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        TransactionHelper.inTransaction(connection -> {
            OrderDAO.LockedOrder order = requireOrder(connection, orderId);
            OrderStatus next;
            switch (order.getStatus()) {
                case RECEIVED:
                    next = OrderStatus.WASHING;
                    break;
                case WASHING:
                    next = OrderStatus.DRYING;
                    break;
                case DRYING:
                    next = OrderStatus.READY_FOR_PICKUP;
                    break;
                case READY_FOR_PICKUP:
                    throw new ServiceException("This order is ready for pickup. Use Release Order when the "
                            + "customer collects it.");
                default:
                    throw new ServiceException("This order is " + order.getStatus().getDisplayName()
                            + " and its status can no longer change.");
            }
            orderDAO.updateStatus(connection, orderId, next);
            orderDAO.insertStatusHistory(connection, orderId, userId, next);
            return null;
        });
        return loadOrder(orderId);
    }

    // ------------------------------------------------------------------
    // Release
    // ------------------------------------------------------------------

    /**
     * Releases an order to the customer. Requires READY_FOR_PICKUP and a zero
     * balance, re-checked with the order row locked, and records the RELEASED
     * history entry in the same transaction.
     */
    public void releaseOrder(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        TransactionHelper.inTransaction(connection -> {
            OrderStatus status = lockOrderStatus(connection, orderId);
            if (status != OrderStatus.READY_FOR_PICKUP) {
                throw new ServiceException("Only orders that are Ready for Pickup can be released. "
                        + "This order is " + status.getDisplayName() + ".");
            }
            BigDecimal balance = currentBalance(connection, orderId);
            if (balance.signum() > 0) {
                throw new ServiceException("Full payment required before release.");
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE orders SET laundry_status = 'RELEASED' WHERE id = ?")) {
                update.setInt(1, orderId);
                update.executeUpdate();
            }
            try (PreparedStatement history = connection.prepareStatement(
                    "INSERT INTO order_status_history (order_id, changed_by, status)"
                    + " VALUES (?, ?, 'RELEASED')")) {
                history.setInt(1, orderId);
                history.setInt(2, userId);
                history.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Checks the fields typed on the order form; returns the cleaned notes. */
    private String validateOrderFields(int customerId, List<OrderItemLine> items, LocalDate expectedCompletionDate,
            String notes, LocalDate receivedDate) throws ServiceException {
        if (customerId <= 0) {
            throw new ServiceException("Select a customer for this order.");
        }
        if (items == null || items.isEmpty()) {
            throw new ServiceException("Add at least one service to the order.");
        }
        validateExpectedDate(expectedCompletionDate, receivedDate);
        return cleanNotes(notes);
    }

    private void validateExpectedDate(LocalDate expectedCompletionDate, LocalDate receivedDate)
            throws ServiceException {
        if (expectedCompletionDate == null) {
            throw new ServiceException("Select the expected completion date.");
        }
        if (expectedCompletionDate.isBefore(receivedDate)) {
            throw new ServiceException("The expected completion date cannot be before the day the order "
                    + "was received.");
        }
    }

    private String cleanNotes(String notes) throws ServiceException {
        String trimmed = notes == null ? "" : notes.trim();
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new ServiceException("Notes can be at most " + MAX_NOTES_LENGTH + " characters.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Builds the rows to save. A line already on the order (same item and service)
     * keeps its saved name/unit/price snapshot; any other line is priced from the
     * services table, which must still offer it.
     */
    private List<OrderItemLine> priceItems(Connection connection, List<OrderItemLine> items,
            Map<Integer, OrderItemLine> savedItems) throws ServiceException, SQLException {
        List<OrderItemLine> priced = new ArrayList<>();
        Set<Integer> serviceIds = new HashSet<>();
        for (OrderItemLine item : items) {
            if (!serviceIds.add(item.getServiceId())) {
                throw new ServiceException(item.getServiceName() + " is listed more than once. "
                        + "Combine it into one line.");
            }
            String name;
            String unit;
            BigDecimal price;
            OrderItemLine saved = savedItems.get(item.getItemId());
            if (saved != null && saved.getServiceId() == item.getServiceId()) {
                name = saved.getServiceName();
                unit = saved.getPricingUnit();
                price = saved.getUnitPrice();
            } else {
                LaundryService service = laundryServiceDAO.findById(connection, item.getServiceId());
                if (service == null || !service.isActive()) {
                    throw new ServiceException(item.getServiceName() + " is no longer offered. "
                            + "Remove it from the order.");
                }
                name = service.getServiceName();
                unit = service.getPricingUnit().name();
                price = service.getCurrentPrice();
            }
            try {
                validateQuantity(item.getQuantity(), unit);
            } catch (ServiceException e) {
                throw new ServiceException(name + ": " + e.getMessage());
            }
            priced.add(new OrderItemLine(0, item.getServiceId(), name, unit, item.getQuantity(), price,
                    subtotal(item.getQuantity(), price)));
        }
        return priced;
    }

    private void requireCustomer(Connection connection, int customerId) throws ServiceException, SQLException {
        if (!orderDAO.customerExists(connection, customerId)) {
            throw new ServiceException("That customer no longer exists. Select another customer.");
        }
    }

    private OrderDAO.LockedOrder requireOrder(Connection connection, int orderId)
            throws ServiceException, SQLException {
        OrderDAO.LockedOrder order = orderDAO.lockOrder(connection, orderId);
        if (order == null) {
            throw new ServiceException("That order no longer exists.");
        }
        return order;
    }

    private OrderStatus lockOrderStatus(Connection connection, int orderId) throws ServiceException, SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT laundry_status FROM orders WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException("That order no longer exists.");
                }
                return OrderStatus.valueOf(rs.getString(1));
            }
        }
    }

    private BigDecimal currentBalance(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM v_order_balances WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }
}

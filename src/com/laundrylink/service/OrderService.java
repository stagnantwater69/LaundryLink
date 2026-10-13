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
import java.util.List;

/**
 * Order rules: create, edit, move through stages, cancel, delete and release.
 * Everything is limited to the logged-in user's shop.
 */
public class OrderService {

    public static final int MAX_NOTES_LENGTH = 500;

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999.99");
    private static final int ORDER_LIST_LIMIT = 2000;

    private final OrderDAO orderDAO = new OrderDAO();
    private final LaundryServiceDAO laundryServiceDAO = new LaundryServiceDAO();
    private final ServiceCatalogService serviceCatalogService = new ServiceCatalogService();

    // ---- Loading ----

    /** Returns the shop's orders, newest first. */
    public List<OrderSummary> loadOrders() throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = ConnectionFactory.getConnection();
        try {
            return orderDAO.findAll(connection, shopId, ORDER_LIST_LIMIT);
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Returns one order with its items and status history. */
    public OrderDetails loadOrder(int orderId) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = ConnectionFactory.getConnection();
        try {
            OrderDetails details = orderDAO.findDetails(connection, shopId, orderId);
            if (details == null) {
                throw new ServiceException("That order no longer exists.");
            }
            return details;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** The shop's customers, for the order form. */
    public List<CustomerOption> loadCustomers() throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = ConnectionFactory.getConnection();
        try {
            return orderDAO.findCustomers(connection, shopId);
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Active services, for the order form. */
    public List<ServiceOption> loadActiveServices() throws ServiceException, SQLException {
        List<ServiceOption> options = new ArrayList<ServiceOption>();
        for (LaundryService service : serviceCatalogService.listActiveServices()) {
            options.add(ServiceOption.from(service));
        }
        return options;
    }

    // ---- Totals ----

    /** Subtotal = quantity x price, rounded to centavos. */
    public static BigDecimal subtotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** Total of all item subtotals. */
    public static BigDecimal total(List<OrderItemLine> items) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (OrderItemLine item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    /** Reads the typed quantity and checks it for the service's unit. */
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

    /** Quantity must be above 0, whole for pieces, and up to 2 decimals for kilos. */
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

    // ---- Create and edit ----

    /** Saves a new order received by the logged-in user. */
    public OrderDetails createOrder(int customerId, List<OrderItemLine> items, LocalDate expectedCompletionDate,
            String notes) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        int shopId = SessionContext.getCurrentShopId();
        String cleanNotes = validateOrderFields(customerId, items, expectedCompletionDate, notes,
                DashboardService.today());

        int orderId;
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            requireCustomer(connection, shopId, customerId);
            List<OrderItemLine> priced = priceItems(connection, shopId, items, new ArrayList<OrderItemLine>());
            orderId = orderDAO.insertOrder(connection, shopId, customerId, userId, expectedCompletionDate, cleanNotes);
            for (OrderItemLine item : priced) {
                orderDAO.insertItem(connection, orderId, item);
            }
            orderDAO.updateTotal(connection, orderId, total(priced));
            orderDAO.insertStatusHistory(connection, orderId, userId, OrderStatus.RECEIVED);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
        return loadOrder(orderId);
    }

    /** Edits an order that's still Received and unpaid. Existing items keep their saved price. */
    public OrderDetails updateOrder(int orderId, int customerId, List<OrderItemLine> items,
            LocalDate expectedCompletionDate, String notes) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
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
            requireCustomer(connection, shopId, customerId);

            List<OrderItemLine> savedItems = orderDAO.findItems(connection, orderId);
            List<OrderItemLine> priced = priceItems(connection, shopId, items, savedItems);
            orderDAO.deleteItems(connection, orderId);
            for (OrderItemLine item : priced) {
                orderDAO.insertItem(connection, orderId, item);
            }
            orderDAO.updateOrder(connection, orderId, customerId, expectedCompletionDate, cleanNotes, total(priced));
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
        return loadOrder(orderId);
    }

    /** Changes only the due date and notes of an open order. */
    public OrderDetails updateOrderSchedule(int orderId, LocalDate expectedCompletionDate, String notes)
            throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
            if (order.getStatus() == OrderStatus.RELEASED || order.getStatus() == OrderStatus.CANCELLED) {
                throw new ServiceException("This order is " + order.getStatus().getDisplayName()
                        + " and can no longer be changed.");
            }
            validateExpectedDate(expectedCompletionDate, order.getReceivedDate());
            orderDAO.updateSchedule(connection, orderId, expectedCompletionDate, cleanNotes(notes));
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
        return loadOrder(orderId);
    }

    // ---- Cancel and delete ----

    /** Cancels an unpaid order that hasn't started. The record is kept. */
    public OrderDetails cancelOrder(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
            if (order.getStatus() != OrderStatus.RECEIVED) {
                throw new ServiceException("Only orders that have not started processing can be cancelled. "
                        + "This order is " + order.getStatus().getDisplayName() + ".");
            }
            if (order.hasPayments()) {
                throw new ServiceException("Orders with recorded payments cannot be cancelled.");
            }
            orderDAO.updateStatus(connection, orderId, OrderStatus.CANCELLED);
            orderDAO.insertStatusHistory(connection, orderId, userId, OrderStatus.CANCELLED);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
        return loadOrder(orderId);
    }

    /** Owner only: deletes an unpaid Received order entered by mistake. */
    public void deleteOrder(int orderId) throws ServiceException, SQLException {
        int shopId = SessionContext.requireAdmin().getShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
            if (order.getStatus() != OrderStatus.RECEIVED || order.hasPayments()) {
                throw new ServiceException("Only unpaid orders that are still Received can be deleted. "
                        + "Cancel the order instead to keep its record.");
            }
            orderDAO.deleteOrder(connection, orderId);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    // ---- Status ----

    /** Moves the order to its next stage and records who did it. */
    public OrderDetails advanceStatus(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
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
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
        return loadOrder(orderId);
    }

    // ---- Release ----

    /** Releases the order. It must be Ready for Pickup and fully paid. */
    public void releaseOrder(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        int shopId = SessionContext.getCurrentShopId();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            OrderStatus status = lockOrderStatus(connection, shopId, orderId);
            if (status != OrderStatus.READY_FOR_PICKUP) {
                throw new ServiceException("Only orders that are Ready for Pickup can be released. "
                        + "This order is " + status.getDisplayName() + ".");
            }
            BigDecimal balance = currentBalance(connection, orderId);
            if (balance.signum() > 0) {
                throw new ServiceException("Full payment required before release.");
            }
            orderDAO.updateStatus(connection, orderId, OrderStatus.RELEASED);
            orderDAO.insertStatusHistory(connection, orderId, userId, OrderStatus.RELEASED);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    // ---- Helpers ----

    /** Checks the form fields and returns the cleaned notes. */
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

    /** Due date is required and can't be before the received date. */
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

    /** Trims the notes, checks the length, and returns null if blank. */
    private String cleanNotes(String notes) throws ServiceException {
        String trimmed = notes == null ? "" : notes.trim();
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new ServiceException("Notes can be at most " + MAX_NOTES_LENGTH + " characters.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Prices each line: saved lines keep their price, new ones use the current price. */
    private List<OrderItemLine> priceItems(Connection connection, int shopId, List<OrderItemLine> items,
            List<OrderItemLine> savedItems) throws ServiceException, SQLException {
        List<OrderItemLine> priced = new ArrayList<OrderItemLine>();
        for (int i = 0; i < items.size(); i++) {
            OrderItemLine item = items.get(i);

            // Each service can only be on an order once.
            for (int j = 0; j < i; j++) {
                if (items.get(j).getServiceId() == item.getServiceId()) {
                    throw new ServiceException(item.getServiceName() + " is listed more than once. "
                            + "Combine it into one line.");
                }
            }

            OrderItemLine saved = findSavedItem(savedItems, item.getItemId());
            String name;
            String unit;
            BigDecimal price;
            if (saved != null && saved.getServiceId() == item.getServiceId()) {
                name = saved.getServiceName();
                unit = saved.getPricingUnit();
                price = saved.getUnitPrice();
            } else {
                LaundryService service = laundryServiceDAO.findById(connection, shopId, item.getServiceId());
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

    /** The saved line with this id, or null if it's new. */
    private OrderItemLine findSavedItem(List<OrderItemLine> savedItems, int itemId) {
        if (itemId <= 0) {
            return null;
        }
        for (OrderItemLine saved : savedItems) {
            if (saved.getItemId() == itemId) {
                return saved;
            }
        }
        return null;
    }

    /** Fails if the customer isn't in this shop. */
    private void requireCustomer(Connection connection, int shopId, int customerId)
            throws ServiceException, SQLException {
        if (!orderDAO.customerExists(connection, shopId, customerId)) {
            throw new ServiceException("That customer no longer exists. Select another customer.");
        }
    }

    /** Locks the order row; fails if it isn't in this shop. */
    private OrderDAO.LockedOrder requireOrder(Connection connection, int shopId, int orderId)
            throws ServiceException, SQLException {
        OrderDAO.LockedOrder order = orderDAO.lockOrder(connection, shopId, orderId);
        if (order == null) {
            throw new ServiceException("That order no longer exists.");
        }
        return order;
    }

    /** Locks the order row and returns its current status. */
    private OrderStatus lockOrderStatus(Connection connection, int shopId, int orderId)
            throws ServiceException, SQLException {
        OrderDAO.LockedOrder order = requireOrder(connection, shopId, orderId);
        return order.getStatus();
    }

    /** Reads the order's unpaid balance. */
    private BigDecimal currentBalance(Connection connection, int orderId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM v_order_balances WHERE order_id = ?");
        try {
            statement.setInt(1, orderId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
            return BigDecimal.ZERO;
        } finally {
            statement.close();
        }
    }
}

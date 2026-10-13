package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.PaymentDAO;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PayableOrder;
import com.laundrylink.model.Payment;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.SessionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment rules (cash only):
 *   - the amount must be above zero, with up to 2 decimals;
 *   - it can't be more than the order's balance;
 *   - the cash given must cover it (the extra is change and isn't stored);
 *   - cancelled and fully paid orders can't take payments.
 */
public class PaymentService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private final PaymentDAO paymentDAO = new PaymentDAO();

    // ---- Reading ----

    /** Payments recorded on one day. */
    public List<Payment> getPaymentsForDay(LocalDate day) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return paymentDAO.findPaymentsByDay(connection, shopId, day.atStartOfDay(), nextDay(day));
        }
    }

    /** All payments of one order. */
    public List<Payment> getPaymentHistory(int orderId) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return paymentDAO.findPaymentsByOrder(connection, shopId, orderId);
        }
    }

    /** Orders that still owe money. */
    public List<PayableOrder> getPayableOrders() throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return paymentDAO.findPayableOrders(connection, shopId);
        }
    }

    /** One order with its totals; fails if it isn't found. */
    public PayableOrder getOrder(int orderId) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            PayableOrder order = paymentDAO.findOrder(connection, shopId, orderId);
            if (order == null) {
                throw new ServiceException("That order no longer exists. Refresh the list and try again.");
            }
            return order;
        }
    }

    /** [0] = cash collected that day, [1] = unpaid balance of that day's orders. */
    public BigDecimal[] getDailyTotals(LocalDate day) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            BigDecimal[] totals = new BigDecimal[2];
            totals[0] = paymentDAO.sumCollected(connection, shopId, day.atStartOfDay(), nextDay(day));
            totals[1] = paymentDAO.sumPending(connection, shopId, day.atStartOfDay(), nextDay(day));
            return totals;
        }
    }

    // ---- Recording a payment ----

    /**
     * Records a cash payment and returns the updated order.
     * The order stays locked while the balance is checked, so nothing slips in between.
     */
    public PayableOrder recordPayment(int orderId, String amountText, String cashText)
            throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        int shopId = SessionContext.getCurrentShopId();
        BigDecimal amount = parseMoney(amountText, "payment amount");
        BigDecimal cash = parseMoney(cashText, "cash received");
        if (cash.compareTo(amount) < 0) {
            throw new ServiceException("Cash received is less than the payment amount.");
        }

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);

            if (!paymentDAO.lockOrder(connection, shopId, orderId)) {
                throw new ServiceException("That order no longer exists. Refresh the list and try again.");
            }
            PayableOrder order = paymentDAO.findOrder(connection, shopId, orderId);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new ServiceException("This order was cancelled. Payments cannot be recorded.");
            }
            if (order.getBalance().signum() <= 0) {
                throw new ServiceException("This order is already fully paid.");
            }
            if (amount.compareTo(order.getBalance()) > 0) {
                throw new ServiceException("Payment amount cannot be more than the remaining balance of "
                        + MoneyFormat.php(order.getBalance()) + ".");
            }

            paymentDAO.insertPayment(connection, orderId, userId, amount);
            PayableOrder updated = paymentDAO.findOrder(connection, shopId, orderId);
            connection.commit();
            return updated;
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

    /** Change to give back, or null if either amount isn't a valid number yet. */
    public static BigDecimal computeChange(String amountText, String cashText) {
        try {
            BigDecimal amount = parseMoney(amountText, "payment amount");
            BigDecimal cash = parseMoney(cashText, "cash received");
            if (cash.compareTo(amount) < 0) {
                return null;
            }
            return cash.subtract(amount);
        } catch (ServiceException e) {
            return null;
        }
    }

    /** Turns "PHP 1,250.50" or "1250.5" into money; fails with a readable message. */
    public static BigDecimal parseMoney(String text, String label) throws ServiceException {
        String clean = text == null ? "" : text.trim();
        if (clean.toUpperCase().startsWith("PHP")) {
            clean = clean.substring(3).trim();
        }
        clean = clean.replace(",", "");
        if (clean.isEmpty()) {
            throw new ServiceException("Enter the " + label + ".");
        }

        BigDecimal value;
        try {
            value = new BigDecimal(clean);
        } catch (NumberFormatException e) {
            throw new ServiceException("The " + label + " must be a number, e.g. 150 or 150.50.");
        }

        if (value.signum() <= 0) {
            throw new ServiceException("The " + label + " must be greater than zero.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("The " + label + " can have at most 2 decimal places.");
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new ServiceException("The " + label + " is too large.");
        }
        return value.setScale(2);
    }

    // ---- Helpers ----

    /** Start of the next day. */
    private static LocalDateTime nextDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay();
    }
}

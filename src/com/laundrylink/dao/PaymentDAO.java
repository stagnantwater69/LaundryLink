package com.laundrylink.dao;

import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PayableOrder;
import com.laundrylink.model.Payment;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.util.MoneyFormat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the Payments tab, always within one shop. A day runs from its
 * start up to (not including) the start of the next day.
 */
public class PaymentDAO {

    private static final String PAYMENT_SELECT =
            "SELECT p.id, p.order_id, COALESCE(o.order_number, CONCAT('#', o.id)) AS order_number,"
            + " CONCAT_WS(' ', c.first_name, NULLIF(c.middle_name, ''), c.last_name) AS customer_name,"
            + " p.amount, p.paid_at,"
            + " CONCAT_WS(' ', u.first_name, u.last_name) AS received_by,"
            + " b.payment_status"
            + " FROM payments p"
            + " JOIN orders o ON o.id = p.order_id"
            + " JOIN customers c ON c.id = o.customer_id"
            + " JOIN users u ON u.id = p.received_by"
            + " JOIN v_order_balances b ON b.order_id = o.id";

    private static final String ORDER_SELECT =
            "SELECT b.order_id, COALESCE(b.order_number, CONCAT('#', b.order_id)) AS order_number,"
            + " CONCAT_WS(' ', c.first_name, NULLIF(c.middle_name, ''), c.last_name) AS customer_name,"
            + " b.laundry_status, b.total_amount, b.amount_paid, b.balance"
            + " FROM v_order_balances b"
            + " JOIN customers c ON c.id = b.customer_id";

    // ---- Payments ----

    /** Payments recorded on one day, newest first. */
    public List<Payment> findPaymentsByDay(Connection connection, int shopId, LocalDateTime dayStart,
            LocalDateTime dayEnd) throws SQLException {
        String sql = PAYMENT_SELECT + " WHERE o.shop_id = ? AND p.paid_at >= ? AND p.paid_at < ?"
                + " ORDER BY p.paid_at DESC, p.id DESC";
        List<Payment> payments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setTimestamp(2, Timestamp.valueOf(dayStart));
            statement.setTimestamp(3, Timestamp.valueOf(dayEnd));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapPayment(rs));
                }
            }
        }
        return payments;
    }

    /** All payments of one order, oldest first. */
    public List<Payment> findPaymentsByOrder(Connection connection, int shopId, int orderId) throws SQLException {
        String sql = PAYMENT_SELECT + " WHERE p.order_id = ? AND o.shop_id = ? ORDER BY p.paid_at, p.id";
        List<Payment> payments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapPayment(rs));
                }
            }
        }
        return payments;
    }

    /** Saves a payment and returns its new id. */
    public int insertPayment(Connection connection, int orderId, int receivedBy, BigDecimal amount)
            throws SQLException {
        String sql = "INSERT INTO payments (order_id, received_by, amount) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, orderId);
            statement.setInt(2, receivedBy);
            statement.setBigDecimal(3, amount);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Payment was saved but no ID was returned.");
    }

    // ---- Orders ----

    /** Orders that still owe money (not cancelled), oldest first. */
    public List<PayableOrder> findPayableOrders(Connection connection, int shopId) throws SQLException {
        String sql = ORDER_SELECT + " WHERE b.shop_id = ? AND b.laundry_status <> 'CANCELLED' AND b.balance > 0"
                + " ORDER BY b.received_at, b.order_id";
        List<PayableOrder> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrder(rs, "", ""));
                }
            }
        }
        return orders;
    }

    /** One order with its service names and "qty x price" text, or null. */
    public PayableOrder findOrder(Connection connection, int shopId, int orderId) throws SQLException {
        String sql = ORDER_SELECT + " WHERE b.order_id = ? AND b.shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String[] itemText = loadItemText(connection, orderId);
                return mapOrder(rs, itemText[0], itemText[1]);
            }
        }
    }

    /** Locks the order row so two cashiers can't pay the same balance at once. False if it's not in this shop. */
    public boolean lockOrder(Connection connection, int shopId, int orderId) throws SQLException {
        String sql = "SELECT id FROM orders WHERE id = ? AND shop_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ---- Daily figures ----

    /** Cash collected: total of the day's payments. */
    public BigDecimal sumCollected(Connection connection, int shopId, LocalDateTime dayStart,
            LocalDateTime dayEnd) throws SQLException {
        String sql = "SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN orders o ON o.id = p.order_id"
                + " WHERE o.shop_id = ? AND p.paid_at >= ? AND p.paid_at < ?";
        return queryMoney(connection, sql, shopId, dayStart, dayEnd);
    }

    /** Pending: unpaid balance of the day's orders (not counting cancelled ones). */
    public BigDecimal sumPending(Connection connection, int shopId, LocalDateTime dayStart,
            LocalDateTime dayEnd) throws SQLException {
        String sql = "SELECT COALESCE(SUM(balance), 0) FROM v_order_balances"
                + " WHERE shop_id = ? AND received_at >= ? AND received_at < ?"
                + " AND laundry_status <> 'CANCELLED' AND balance > 0";
        return queryMoney(connection, sql, shopId, dayStart, dayEnd);
    }

    // ---- Helpers ----

    /** [0] = "Wash & Fold, Ironing", [1] = "4 kg x PHP 65.00, 2 pcs x PHP 30.00" */
    private String[] loadItemText(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT service_name_snapshot, pricing_unit_snapshot, quantity, unit_price"
                + " FROM order_items WHERE order_id = ? ORDER BY id";
        StringBuilder services = new StringBuilder();
        StringBuilder quantities = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (services.length() > 0) {
                        services.append(", ");
                        quantities.append(", ");
                    }
                    services.append(rs.getString("service_name_snapshot"));
                    quantities.append(MoneyFormat.quantityTimesPrice(rs.getBigDecimal("quantity"),
                            rs.getString("pricing_unit_snapshot"), rs.getBigDecimal("unit_price")));
                }
            }
        }
        return new String[] {services.toString(), quantities.toString()};
    }

    /** Builds a Payment from the current row. */
    private Payment mapPayment(ResultSet rs) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new Payment(
                rs.getInt("id"),
                rs.getInt("order_id"),
                rs.getString("order_number"),
                rs.getString("customer_name"),
                rs.getBigDecimal("amount"),
                paidAt == null ? null : paidAt.toLocalDateTime(),
                rs.getString("received_by"),
                PaymentStatus.valueOf(rs.getString("payment_status")));
    }

    /** Builds a PayableOrder from the current row. */
    private PayableOrder mapOrder(ResultSet rs, String services, String quantityText) throws SQLException {
        return new PayableOrder(
                rs.getInt("order_id"),
                rs.getString("order_number"),
                rs.getString("customer_name"),
                services,
                quantityText,
                OrderStatus.valueOf(rs.getString("laundry_status")),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("amount_paid"),
                rs.getBigDecimal("balance"));
    }

    /** Runs a SUM for one shop and day (0 if there's nothing to add). */
    private BigDecimal queryMoney(Connection connection, String sql, int shopId, LocalDateTime start,
            LocalDateTime end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setTimestamp(2, Timestamp.valueOf(start));
            statement.setTimestamp(3, Timestamp.valueOf(end));
            try (ResultSet rs = statement.executeQuery()) {
                BigDecimal value = null;
                if (rs.next()) {
                    value = rs.getBigDecimal(1);
                }
                if (value == null) {
                    return BigDecimal.ZERO;
                }
                return value;
            }
        }
    }
}

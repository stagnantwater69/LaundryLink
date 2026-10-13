package com.laundrylink.dao;

import com.laundrylink.model.DashboardStats;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.model.PaymentStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard figures and recent orders for one shop. A day runs from its start to the next day's start.
 */
public class DashboardDAO {

    private static final String ORDER_SUMMARY_SELECT =
            "SELECT o.id, o.order_number,"
            + " CONCAT_WS(' ', c.first_name, NULLIF(c.middle_name, ''), c.last_name) AS customer_name,"
            + " o.received_at, o.laundry_status,"
            + " b.total_amount, b.amount_paid, b.balance, b.payment_status,"
            + " (SELECT GROUP_CONCAT(i.service_name_snapshot ORDER BY i.id SEPARATOR ', ')"
            + "    FROM order_items i WHERE i.order_id = o.id) AS services"
            + " FROM orders o"
            + " JOIN customers c ON c.id = o.customer_id"
            + " JOIN v_order_balances b ON b.order_id = o.id";

    /** The day's orders, payments and releases, plus the current workload. */
    public DashboardStats loadStats(Connection connection, int shopId, LocalDateTime dayStart,
            LocalDateTime dayEnd) throws SQLException {
        int ordersReceived = countForDay(connection,
                "SELECT COUNT(*) FROM orders WHERE shop_id = ? AND received_at >= ? AND received_at < ?"
                + " AND laundry_status <> 'CANCELLED'", shopId, dayStart, dayEnd);
        BigDecimal paymentsCollected = sumForDay(connection,
                "SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN orders o ON o.id = p.order_id"
                + " WHERE o.shop_id = ? AND p.paid_at >= ? AND p.paid_at < ?",
                shopId, dayStart, dayEnd);
        int released = countForDay(connection,
                "SELECT COUNT(DISTINCT h.order_id) FROM order_status_history h JOIN orders o ON o.id = h.order_id"
                + " WHERE o.shop_id = ? AND h.status = 'RELEASED' AND h.changed_at >= ? AND h.changed_at < ?",
                shopId, dayStart, dayEnd);
        BigDecimal outstanding = sumForShop(connection,
                "SELECT COALESCE(SUM(balance), 0) FROM v_order_balances"
                + " WHERE shop_id = ? AND laundry_status <> 'CANCELLED' AND balance > 0", shopId);

        int received = 0;
        int washing = 0;
        int drying = 0;
        int ready = 0;
        String countsSql = "SELECT laundry_status, COUNT(*) FROM orders"
                + " WHERE shop_id = ? AND laundry_status IN ('RECEIVED', 'WASHING', 'DRYING', 'READY_FOR_PICKUP')"
                + " GROUP BY laundry_status";
        try (PreparedStatement statement = connection.prepareStatement(countsSql)) {
            statement.setInt(1, shopId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                int count = rs.getInt(2);
                switch (OrderStatus.valueOf(rs.getString(1))) {
                    case RECEIVED:
                        received = count;
                        break;
                    case WASHING:
                        washing = count;
                        break;
                    case DRYING:
                        drying = count;
                        break;
                    default:
                        ready = count;
                        break;
                }
            }
        }
        return new DashboardStats(ordersReceived, paymentsCollected, released, ready, outstanding,
                received, washing, drying);
    }

    /** Newest orders first (cancelled ones too, so they can still be found). */
    public List<OrderSummary> findRecentOrders(Connection connection, int shopId, int limit) throws SQLException {
        String sql = ORDER_SUMMARY_SELECT + " WHERE o.shop_id = ? ORDER BY o.received_at DESC, o.id DESC LIMIT ?";
        List<OrderSummary> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrderSummary(rs));
                }
            }
        }
        return orders;
    }

    /** One order of the shop, or null. */
    public OrderSummary findOrderSummary(Connection connection, int shopId, int orderId) throws SQLException {
        String sql = ORDER_SUMMARY_SELECT + " WHERE o.id = ? AND o.shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapOrderSummary(rs) : null;
            }
        }
    }

    /** The service lines of one order. */
    public List<OrderItemLine> findOrderItems(Connection connection, int shopId, int orderId) throws SQLException {
        String sql = "SELECT i.service_name_snapshot, i.pricing_unit_snapshot, i.quantity, i.unit_price, i.subtotal"
                + " FROM order_items i JOIN orders o ON o.id = i.order_id"
                + " WHERE i.order_id = ? AND o.shop_id = ? ORDER BY i.id";
        List<OrderItemLine> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new OrderItemLine(
                            rs.getString("service_name_snapshot"),
                            rs.getString("pricing_unit_snapshot"),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("subtotal")));
                }
            }
        }
        return items;
    }

    /** Builds an OrderSummary from the current row. */
    private OrderSummary mapOrderSummary(ResultSet rs) throws SQLException {
        String orderNumber = rs.getString("order_number");
        return new OrderSummary(
                rs.getInt("id"),
                orderNumber == null ? "#" + rs.getInt("id") : orderNumber,
                rs.getString("customer_name"),
                rs.getString("services") == null ? "" : rs.getString("services"),
                rs.getObject("received_at", LocalDateTime.class),
                OrderStatus.valueOf(rs.getString("laundry_status")),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("amount_paid"),
                rs.getBigDecimal("balance"),
                PaymentStatus.valueOf(rs.getString("payment_status")));
    }

    /** Runs a COUNT for one shop and day. */
    private int countForDay(Connection connection, String sql, int shopId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setTimestamp(2, Timestamp.valueOf(start));
            statement.setTimestamp(3, Timestamp.valueOf(end));
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Runs a SUM for one shop and day (0 if there's nothing to add). */
    private BigDecimal sumForDay(Connection connection, String sql, int shopId, LocalDateTime start,
            LocalDateTime end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setTimestamp(2, Timestamp.valueOf(start));
            statement.setTimestamp(3, Timestamp.valueOf(end));
            return readMoney(statement.executeQuery());
        }
    }

    /** Runs a SUM for one shop. */
    private BigDecimal sumForShop(Connection connection, String sql, int shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            return readMoney(statement.executeQuery());
        }
    }

    /** The single money value of a result, or 0. */
    private BigDecimal readMoney(ResultSet rs) throws SQLException {
        BigDecimal value = null;
        if (rs.next()) {
            value = rs.getBigDecimal(1);
        }
        return value == null ? BigDecimal.ZERO : value;
    }
}

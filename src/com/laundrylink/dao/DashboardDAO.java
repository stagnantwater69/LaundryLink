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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only queries for the dashboard. Each figure is aggregated on its own so
 * joins never multiply totals. Day ranges are [start, end): start of the
 * selected day inclusive, start of the next day exclusive.
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

    public DashboardStats loadStats(Connection connection, LocalDateTime dayStart, LocalDateTime dayEnd)
            throws SQLException {
        int ordersReceived = queryInt(connection,
                "SELECT COUNT(*) FROM orders WHERE received_at >= ? AND received_at < ?"
                + " AND laundry_status <> 'CANCELLED'", dayStart, dayEnd);
        BigDecimal paymentsCollected = queryMoney(connection,
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE paid_at >= ? AND paid_at < ?",
                dayStart, dayEnd);
        int released = queryInt(connection,
                "SELECT COUNT(DISTINCT order_id) FROM order_status_history"
                + " WHERE status = 'RELEASED' AND changed_at >= ? AND changed_at < ?", dayStart, dayEnd);
        BigDecimal outstanding = queryMoney(connection,
                "SELECT COALESCE(SUM(balance), 0) FROM v_order_balances"
                + " WHERE laundry_status <> 'CANCELLED' AND balance > 0");

        int received = 0;
        int washing = 0;
        int drying = 0;
        int ready = 0;
        String countsSql = "SELECT laundry_status, COUNT(*) FROM orders"
                + " WHERE laundry_status IN ('RECEIVED', 'WASHING', 'DRYING', 'READY_FOR_PICKUP')"
                + " GROUP BY laundry_status";
        try (PreparedStatement statement = connection.prepareStatement(countsSql);
                ResultSet rs = statement.executeQuery()) {
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

    /** Most recent orders first (cancelled included so they can still be looked up). */
    public List<OrderSummary> findRecentOrders(Connection connection, int limit) throws SQLException {
        String sql = ORDER_SUMMARY_SELECT + " ORDER BY o.received_at DESC, o.id DESC LIMIT ?";
        List<OrderSummary> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrderSummary(rs));
                }
            }
        }
        return orders;
    }

    public OrderSummary findOrderSummary(Connection connection, int orderId) throws SQLException {
        String sql = ORDER_SUMMARY_SELECT + " WHERE o.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapOrderSummary(rs) : null;
            }
        }
    }

    public List<OrderItemLine> findOrderItems(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT service_name_snapshot, pricing_unit_snapshot, quantity, unit_price, subtotal"
                + " FROM order_items WHERE order_id = ? ORDER BY id";
        List<OrderItemLine> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
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

    private int queryInt(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private BigDecimal queryMoney(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rs = statement.executeQuery()) {
            BigDecimal value = rs.next() ? rs.getBigDecimal(1) : null;
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }
}

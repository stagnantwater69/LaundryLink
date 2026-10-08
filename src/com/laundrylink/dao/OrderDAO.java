package com.laundrylink.dao;

import com.laundrylink.model.CustomerOption;
import com.laundrylink.model.OrderDetails;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.model.StatusChange;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries and updates for laundry orders and their items. Business rules
 * live in OrderService; this class only reads and writes rows.
 */
public class OrderDAO {

    private static final String ORDER_SUMMARY_COLUMNS =
            "o.id, o.order_number,"
            + " CONCAT_WS(' ', c.first_name, NULLIF(c.middle_name, ''), c.last_name) AS customer_name,"
            + " c.contact_number, o.received_at, o.expected_completion_date, o.laundry_status,"
            + " b.total_amount, b.amount_paid, b.balance, b.payment_status,"
            + " (SELECT GROUP_CONCAT(i.service_name_snapshot ORDER BY i.id SEPARATOR ', ')"
            + "    FROM order_items i WHERE i.order_id = o.id) AS services";

    private static final String ORDER_SUMMARY_FROM =
            " FROM orders o"
            + " JOIN customers c ON c.id = o.customer_id"
            + " JOIN v_order_balances b ON b.order_id = o.id";

    /** The current state of an order row, read with the row locked. */
    public static final class LockedOrder {

        private final OrderStatus status;
        private final LocalDate receivedDate;
        private final BigDecimal amountPaid;

        LockedOrder(OrderStatus status, LocalDate receivedDate, BigDecimal amountPaid) {
            this.status = status;
            this.receivedDate = receivedDate;
            this.amountPaid = amountPaid;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public LocalDate getReceivedDate() {
            return receivedDate;
        }

        public boolean hasPayments() {
            return amountPaid.signum() > 0;
        }
    }

    // ------------------------------------------------------------------
    // Reading orders
    // ------------------------------------------------------------------

    /** Newest orders first, cancelled and released included. */
    public List<OrderSummary> findAll(Connection connection, int limit) throws SQLException {
        String sql = "SELECT " + ORDER_SUMMARY_COLUMNS + ORDER_SUMMARY_FROM
                + " ORDER BY o.received_at DESC, o.id DESC LIMIT ?";
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

    public OrderDetails findDetails(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT " + ORDER_SUMMARY_COLUMNS + ","
                + " o.customer_id, o.notes,"
                + " CONCAT_WS(' ', u.first_name, u.last_name) AS created_by_name"
                + ORDER_SUMMARY_FROM
                + " JOIN users u ON u.id = o.created_by"
                + " WHERE o.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new OrderDetails(
                        mapOrderSummary(rs),
                        rs.getInt("customer_id"),
                        rs.getObject("expected_completion_date", LocalDate.class),
                        rs.getString("notes"),
                        rs.getString("created_by_name"),
                        findItems(connection, orderId),
                        findStatusHistory(connection, orderId));
            }
        }
    }

    public List<OrderItemLine> findItems(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT id, service_id, service_name_snapshot, pricing_unit_snapshot, quantity, unit_price, subtotal"
                + " FROM order_items WHERE order_id = ? ORDER BY id";
        List<OrderItemLine> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new OrderItemLine(
                            rs.getInt("id"),
                            rs.getInt("service_id"),
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

    /** Oldest first, with the name of the staff member who made each change. */
    public List<StatusChange> findStatusHistory(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT h.changed_at, h.status, CONCAT_WS(' ', u.first_name, u.last_name) AS changed_by_name"
                + " FROM order_status_history h JOIN users u ON u.id = h.changed_by"
                + " WHERE h.order_id = ? ORDER BY h.changed_at, h.id";
        List<StatusChange> history = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    history.add(new StatusChange(
                            rs.getObject("changed_at", LocalDateTime.class),
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getString("changed_by_name")));
                }
            }
        }
        return history;
    }

    /**
     * Locks the order row (SELECT ... FOR UPDATE) so its status and payments
     * cannot change until the transaction ends. Returns null if it does not exist.
     */
    public LockedOrder lockOrder(Connection connection, int orderId) throws SQLException {
        String sql = "SELECT laundry_status, received_at,"
                + " (SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.order_id = o.id) AS amount_paid"
                + " FROM orders o WHERE o.id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new LockedOrder(
                        OrderStatus.valueOf(rs.getString("laundry_status")),
                        rs.getObject("received_at", LocalDateTime.class).toLocalDate(),
                        rs.getBigDecimal("amount_paid"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Lookups for the order form
    // ------------------------------------------------------------------

    /** All customers, sorted by last name then first name. */
    public List<CustomerOption> findCustomers(Connection connection) throws SQLException {
        String sql = "SELECT id, CONCAT_WS(' ', first_name, NULLIF(middle_name, ''), last_name) AS name,"
                + " contact_number FROM customers ORDER BY last_name, first_name, id";
        List<CustomerOption> customers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                customers.add(new CustomerOption(rs.getInt("id"), rs.getString("name"),
                        rs.getString("contact_number")));
            }
        }
        return customers;
    }

    public boolean customerExists(Connection connection, int customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM customers WHERE id = ?")) {
            statement.setInt(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ------------------------------------------------------------------
    // Writing orders
    // ------------------------------------------------------------------

    /** Inserts the order and assigns its order number (LL-000123); returns the new id. */
    public int insertOrder(Connection connection, int customerId, int createdBy, LocalDate expectedCompletionDate,
            String notes) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, created_by, expected_completion_date, notes)"
                + " VALUES (?, ?, ?, ?)";
        int orderId;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, customerId);
            statement.setInt(2, createdBy);
            statement.setObject(3, expectedCompletionDate);
            statement.setString(4, notes);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating the order returned no id.");
                }
                orderId = keys.getInt(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET order_number = CONCAT('LL-', LPAD(id, 6, '0')) WHERE id = ?")) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
        return orderId;
    }

    public void insertItem(Connection connection, int orderId, OrderItemLine item) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, service_id, service_name_snapshot, pricing_unit_snapshot,"
                + " quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, item.getServiceId());
            statement.setString(3, item.getServiceName());
            statement.setString(4, item.getPricingUnit());
            statement.setBigDecimal(5, item.getQuantity());
            statement.setBigDecimal(6, item.getUnitPrice());
            statement.setBigDecimal(7, item.getSubtotal());
            statement.executeUpdate();
        }
    }

    public void deleteItems(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM order_items WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
    }

    public void updateOrder(Connection connection, int orderId, int customerId, LocalDate expectedCompletionDate,
            String notes, BigDecimal totalAmount) throws SQLException {
        String sql = "UPDATE orders SET customer_id = ?, expected_completion_date = ?, notes = ?, total_amount = ?"
                + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setObject(2, expectedCompletionDate);
            statement.setString(3, notes);
            statement.setBigDecimal(4, totalAmount);
            statement.setInt(5, orderId);
            statement.executeUpdate();
        }
    }

    public void updateTotal(Connection connection, int orderId, BigDecimal totalAmount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET total_amount = ? WHERE id = ?")) {
            statement.setBigDecimal(1, totalAmount);
            statement.setInt(2, orderId);
            statement.executeUpdate();
        }
    }

    public void updateSchedule(Connection connection, int orderId, LocalDate expectedCompletionDate, String notes)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET expected_completion_date = ?, notes = ? WHERE id = ?")) {
            statement.setObject(1, expectedCompletionDate);
            statement.setString(2, notes);
            statement.setInt(3, orderId);
            statement.executeUpdate();
        }
    }

    public void updateStatus(Connection connection, int orderId, OrderStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET laundry_status = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setInt(2, orderId);
            statement.executeUpdate();
        }
    }

    public void insertStatusHistory(Connection connection, int orderId, int changedBy, OrderStatus status)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO order_status_history (order_id, changed_by, status) VALUES (?, ?, ?)")) {
            statement.setInt(1, orderId);
            statement.setInt(2, changedBy);
            statement.setString(3, status.name());
            statement.executeUpdate();
        }
    }

    /** Deletes the order; its items and status history are removed by ON DELETE CASCADE. */
    public void deleteOrder(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM orders WHERE id = ?")) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

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
                PaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getString("contact_number"),
                rs.getObject("expected_completion_date", LocalDate.class));
    }
}

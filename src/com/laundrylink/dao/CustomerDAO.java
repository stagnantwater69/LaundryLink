package com.laundrylink.dao;

import com.laundrylink.model.Customer;
import com.laundrylink.model.CustomerOrderSummary;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.model.Sex;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** JDBC queries for customers and their order history. Every query is limited to one shop. */
public class CustomerDAO {

    private static final String CUSTOMER_COLUMNS =
            "id, first_name, middle_name, last_name, sex, contact_number, address, created_at,"
            + " (SELECT COUNT(*) FROM orders WHERE orders.customer_id = customers.id) AS order_count";

    public List<Customer> findAll(Connection connection, int shopId) throws SQLException {
        return find(connection, shopId, "");
    }

    public List<Customer> find(Connection connection, int shopId, String search) throws SQLException {
        String clean = search == null ? "" : search.trim();
        String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customers "
                + "WHERE shop_id = ? AND (? = '' OR CONCAT_WS(' ', first_name, middle_name, last_name) LIKE ? "
                + "OR contact_number LIKE ?) "
                + "ORDER BY last_name, first_name, middle_name";
        List<Customer> customers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String like = "%" + clean + "%";
            statement.setInt(1, shopId);
            statement.setString(2, clean);
            statement.setString(3, like);
            statement.setString(4, like);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    customers.add(mapCustomer(rs));
                }
            }
        }
        return customers;
    }

    /** Returns null when the customer does not exist or belongs to another shop. */
    public Customer findById(Connection connection, int shopId, int id) throws SQLException {
        String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customers WHERE id = ? AND shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapCustomer(rs) : null;
            }
        }
    }

    public int insert(Connection connection, int shopId, String firstName, String middleName, String lastName,
            Sex sex, String contactNumber, String address) throws SQLException {
        String sql = "INSERT INTO customers (shop_id, first_name, middle_name, last_name, contact_number, address, sex)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, shopId);
            statement.setString(2, firstName);
            setNullable(statement, 3, middleName);
            statement.setString(4, lastName);
            setNullable(statement, 5, contactNumber);
            setNullable(statement, 6, address);
            setNullable(statement, 7, sex == null ? null : sex.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Customer was saved but no ID was returned.");
    }

    public boolean update(Connection connection, int shopId, int id, String firstName, String middleName,
            String lastName, Sex sex, String contactNumber, String address) throws SQLException {
        String sql = "UPDATE customers SET first_name = ?, middle_name = ?, last_name = ?, "
                + "contact_number = ?, address = ?, sex = ? WHERE id = ? AND shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, firstName);
            setNullable(statement, 2, middleName);
            statement.setString(3, lastName);
            setNullable(statement, 4, contactNumber);
            setNullable(statement, 5, address);
            setNullable(statement, 6, sex == null ? null : sex.name());
            statement.setInt(7, id);
            statement.setInt(8, shopId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Deletes only when customer has no orders. Single SQL statement keeps rule atomic. */
    public boolean deleteIfNoOrders(Connection connection, int shopId, int id) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ? AND shop_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM orders WHERE orders.customer_id = customers.id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, shopId);
            return statement.executeUpdate() == 1;
        }
    }

    public int countOrders(Connection connection, int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE customer_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<CustomerOrderSummary> findOrderHistory(Connection connection, int shopId, int customerId)
            throws SQLException {
        String sql = "SELECT v.order_id, v.order_number, v.received_at, "
                + "COALESCE(GROUP_CONCAT(oi.service_name_snapshot ORDER BY oi.id SEPARATOR ', '), '') AS services, "
                + "v.laundry_status, v.total_amount, v.amount_paid, v.balance, v.payment_status "
                + "FROM v_order_balances v "
                + "LEFT JOIN order_items oi ON oi.order_id = v.order_id "
                + "WHERE v.customer_id = ? AND v.shop_id = ? "
                + "GROUP BY v.order_id, v.order_number, v.received_at, v.laundry_status, "
                + "v.total_amount, v.amount_paid, v.balance, v.payment_status "
                + "ORDER BY v.received_at DESC, v.order_id DESC";
        List<CustomerOrderSummary> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(new CustomerOrderSummary(
                            rs.getInt("order_id"),
                            rs.getString("order_number"),
                            toLocalDateTime(rs.getTimestamp("received_at")),
                            rs.getString("services"),
                            OrderStatus.valueOf(rs.getString("laundry_status")),
                            rs.getBigDecimal("total_amount"),
                            rs.getBigDecimal("amount_paid"),
                            rs.getBigDecimal("balance"),
                            PaymentStatus.valueOf(rs.getString("payment_status"))));
                }
            }
        }
        return orders;
    }

    private Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getInt("id"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getString("sex") == null ? null : Sex.valueOf(rs.getString("sex")),
                rs.getString("contact_number"),
                rs.getString("address"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                rs.getInt("order_count"));
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}

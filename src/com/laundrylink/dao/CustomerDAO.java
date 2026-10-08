package com.laundrylink.dao;

import com.laundrylink.model.Customer;
import com.laundrylink.model.CustomerOrderSummary;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PaymentStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** JDBC queries for customers and their order history. */
public class CustomerDAO {

    private static final String CUSTOMER_COLUMNS =
            "id, first_name, middle_name, last_name, contact_number, address, created_at";

    public List<Customer> findAll(Connection connection) throws SQLException {
        return find(connection, "");
    }

    public List<Customer> find(Connection connection, String search) throws SQLException {
        String clean = search == null ? "" : search.trim();
        String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customers "
                + "WHERE ? = '' OR CONCAT_WS(' ', first_name, middle_name, last_name) LIKE ? "
                + "OR contact_number LIKE ? "
                + "ORDER BY last_name, first_name, middle_name";
        List<Customer> customers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String like = "%" + clean + "%";
            statement.setString(1, clean);
            statement.setString(2, like);
            statement.setString(3, like);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    customers.add(mapCustomer(rs));
                }
            }
        }
        return customers;
    }

    public Customer findById(Connection connection, int id) throws SQLException {
        String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customers WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapCustomer(rs) : null;
            }
        }
    }

    public int insert(Connection connection, String firstName, String middleName, String lastName,
            String contactNumber, String address) throws SQLException {
        String sql = "INSERT INTO customers "
                + "(first_name, middle_name, last_name, contact_number, address) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, firstName);
            setNullable(statement, 2, middleName);
            statement.setString(3, lastName);
            setNullable(statement, 4, contactNumber);
            setNullable(statement, 5, address);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Customer was saved but no ID was returned.");
    }

    public boolean update(Connection connection, int id, String firstName, String middleName, String lastName,
            String contactNumber, String address) throws SQLException {
        String sql = "UPDATE customers SET first_name = ?, middle_name = ?, last_name = ?, "
                + "contact_number = ?, address = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, firstName);
            setNullable(statement, 2, middleName);
            statement.setString(3, lastName);
            setNullable(statement, 4, contactNumber);
            setNullable(statement, 5, address);
            statement.setInt(6, id);
            return statement.executeUpdate() == 1;
        }
    }

    /** Deletes only when customer has no orders. Single SQL statement keeps rule atomic. */
    public boolean deleteIfNoOrders(Connection connection, int id) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM orders WHERE orders.customer_id = customers.id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
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

    public List<CustomerOrderSummary> findOrderHistory(Connection connection, int customerId) throws SQLException {
        String sql = "SELECT v.order_id, v.order_number, v.received_at, "
                + "COALESCE(GROUP_CONCAT(oi.service_name_snapshot ORDER BY oi.id SEPARATOR ', '), '') AS services, "
                + "v.laundry_status, v.total_amount, v.amount_paid, v.balance, v.payment_status "
                + "FROM v_order_balances v "
                + "LEFT JOIN order_items oi ON oi.order_id = v.order_id "
                + "WHERE v.customer_id = ? "
                + "GROUP BY v.order_id, v.order_number, v.received_at, v.laundry_status, "
                + "v.total_amount, v.amount_paid, v.balance, v.payment_status "
                + "ORDER BY v.received_at DESC, v.order_id DESC";
        List<CustomerOrderSummary> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
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
                rs.getString("contact_number"),
                rs.getString("address"),
                toLocalDateTime(rs.getTimestamp("created_at")));
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

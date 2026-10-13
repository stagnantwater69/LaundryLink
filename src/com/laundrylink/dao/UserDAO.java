package com.laundrylink.dao;

import com.laundrylink.model.Role;
import com.laundrylink.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the users table. The caller passes the connection so several
 * calls can share one transaction.
 */
public class UserDAO {

    private static final String SELECT_USER =
            "SELECT u.id, u.shop_id, s.shop_name, u.first_name, u.middle_name, u.last_name, u.username,"
            + " u.password_hash, u.role, u.is_active, u.created_at"
            + " FROM users u JOIN shops s ON s.id = u.shop_id";

    /** The account with this id (and its shop name), or null. */
    public User findById(Connection connection, int id) throws SQLException {
        String sql = SELECT_USER + " WHERE u.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** The account with this username (for login), or null. */
    public User findByUsername(Connection connection, String username) throws SQLException {
        String sql = SELECT_USER + " WHERE u.username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Staff accounts of one shop (owners are managed in My Profile). */
    public List<User> findAllStaff(Connection connection, int shopId) throws SQLException {
        String sql = SELECT_USER + " WHERE u.shop_id = ? AND u.role = 'STAFF'"
                + " ORDER BY u.is_active DESC, u.last_name, u.first_name";
        List<User> users = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    /** True once any shop has been registered. */
    public boolean anyShopExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM shops LIMIT 1");
                ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    /** Creates a shop and returns its id. */
    public int insertShop(Connection connection, String shopName) throws SQLException {
        String sql = "INSERT INTO shops (shop_name) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, shopName);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Shop was saved but no ID was returned.");
    }

    /** True if another account already has this username. */
    public boolean usernameExists(Connection connection, String username, int excludeUserId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ? AND id <> ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setInt(2, excludeUserId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Saves a new account and returns its id. */
    public int insert(Connection connection, int shopId, String firstName, String middleName, String lastName,
            String username, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users (shop_id, first_name, middle_name, last_name, username, password_hash, role,"
                + " is_active) VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, shopId);
            statement.setString(2, firstName);
            statement.setString(3, middleName);
            statement.setString(4, lastName);
            statement.setString(5, username);
            statement.setString(6, passwordHash);
            statement.setString(7, role.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Account was saved but no ID was returned.");
    }

    /** Saves an account's name, username and role. */
    public void update(Connection connection, int id, String firstName, String middleName, String lastName,
            String username, Role role) throws SQLException {
        String sql = "UPDATE users SET first_name = ?, middle_name = ?, last_name = ?, username = ?, role = ?"
                + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, firstName);
            statement.setString(2, middleName);
            statement.setString(3, lastName);
            statement.setString(4, username);
            statement.setString(5, role.name());
            statement.setInt(6, id);
            statement.executeUpdate();
        }
    }

    /** Turns an account's login on or off. */
    public void updateActive(Connection connection, int id, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    /** Saves a new password hash. */
    public void updatePassword(Connection connection, int id, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    /** Builds a User from the current row. */
    private User mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("id"),
                rs.getInt("shop_id"),
                rs.getString("shop_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getString("username"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("is_active"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}

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
 * JDBC queries for the users table. The caller supplies the connection so
 * several calls can share one transaction.
 */
public class UserDAO {

    private static final String COLUMNS =
            "id, first_name, middle_name, last_name, username, password_hash, role, is_active, created_at";

    public User findById(Connection connection, int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public User findByUsername(Connection connection, String username) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Staff accounts only (the owner is managed in My Profile). */
    public List<User> findAllStaff(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE role = 'STAFF'"
                + " ORDER BY is_active DESC, last_name, first_name";
        List<User> users = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    /**
     * Checks whether the owner account (role ADMIN) exists. With lockForUpdate inside a
     * transaction, concurrent first-run registrations cannot both succeed.
     */
    public boolean adminExists(Connection connection, boolean lockForUpdate) throws SQLException {
        String sql = "SELECT id FROM users WHERE role = 'ADMIN' LIMIT 1" + (lockForUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    /**
     * Serializes first-run owner setup across connections. The lock is held by
     * this connection's session and released when the connection closes.
     */
    public void lockOwnerSetup(Connection connection) throws SQLException {
        String sql = "SELECT GET_LOCK('laundrylink.owner_setup', 10)";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new SQLException("Owner setup is busy. Please try again.");
            }
        }
    }

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

    public int insert(Connection connection, String firstName, String middleName, String lastName,
            String username, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users (first_name, middle_name, last_name, username, password_hash, role, is_active)"
                + " VALUES (?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, firstName);
            statement.setString(2, middleName);
            statement.setString(3, lastName);
            statement.setString(4, username);
            statement.setString(5, passwordHash);
            statement.setString(6, role.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Account was saved but no ID was returned.");
    }

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

    public void updateActive(Connection connection, int id, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public void updatePassword(Connection connection, int id, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("id"),
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

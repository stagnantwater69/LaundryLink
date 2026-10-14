package com.laundrylink.dao;

import com.laundrylink.model.LaundryService;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PricingUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC queries for the services table. The caller supplies the connection so
 * several calls can share one transaction. Every query is limited to one shop.
 */
public class LaundryServiceDAO {

    private static final String COLUMNS =
            "id, service_name, pricing_unit, current_price, is_active, created_at,"
            + " needs_washing, needs_drying, needs_ironing";

    /** Returns every service for the owner's management screen. */
    public List<LaundryService> findAll(Connection connection, int shopId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE shop_id = ?"
                + " ORDER BY is_active DESC, service_name";
        return findMany(connection, sql, shopId);
    }

    /** Returns only services that may be selected for a new laundry order. */
    public List<LaundryService> findAllActive(Connection connection, int shopId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE shop_id = ? AND is_active = 1"
                + " ORDER BY service_name";
        return findMany(connection, sql, shopId);
    }

    /** Returns null when the service does not exist or belongs to another shop. */
    public LaundryService findById(Connection connection, int shopId, int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE id = ? AND shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public boolean serviceNameExists(Connection connection, int shopId, String serviceName, int excludeServiceId)
            throws SQLException {
        String sql = "SELECT 1 FROM services WHERE shop_id = ? AND service_name = ? AND id <> ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            statement.setString(2, serviceName);
            statement.setInt(3, excludeServiceId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int insert(Connection connection, int shopId, String serviceName, PricingUnit pricingUnit,
            java.math.BigDecimal currentPrice, List<OrderStatus> stages) throws SQLException {
        String sql = "INSERT INTO services (shop_id, service_name, pricing_unit, current_price, is_active,"
                + " needs_washing, needs_drying, needs_ironing) VALUES (?, ?, ?, ?, 1, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, shopId);
            statement.setString(2, serviceName);
            statement.setString(3, pricingUnit.name());
            statement.setBigDecimal(4, currentPrice);
            setStages(statement, 5, stages);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Service was saved but no ID was returned.");
    }

    public boolean update(Connection connection, int shopId, int id, String serviceName,
            PricingUnit pricingUnit, java.math.BigDecimal currentPrice, List<OrderStatus> stages)
            throws SQLException {
        String sql = "UPDATE services SET service_name = ?, pricing_unit = ?, current_price = ?,"
                + " needs_washing = ?, needs_drying = ?, needs_ironing = ?"
                + " WHERE id = ? AND shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serviceName);
            statement.setString(2, pricingUnit.name());
            statement.setBigDecimal(3, currentPrice);
            setStages(statement, 4, stages);
            statement.setInt(7, id);
            statement.setInt(8, shopId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateActiveStatus(Connection connection, int shopId, int id, boolean active)
            throws SQLException {
        String sql = "UPDATE services SET is_active = ? WHERE id = ? AND shop_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.setInt(3, shopId);
            return statement.executeUpdate() == 1;
        }
    }

    private List<LaundryService> findMany(Connection connection, String sql, int shopId) throws SQLException {
        List<LaundryService> services = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    services.add(mapRow(rs));
                }
            }
        }
        return services;
    }

    /** Fills the three needs_* parameters, starting at the given index. */
    private static void setStages(PreparedStatement statement, int firstIndex, List<OrderStatus> stages)
            throws SQLException {
        statement.setBoolean(firstIndex, stages.contains(OrderStatus.WASHING));
        statement.setBoolean(firstIndex + 1, stages.contains(OrderStatus.DRYING));
        statement.setBoolean(firstIndex + 2, stages.contains(OrderStatus.IRONING));
    }

    private LaundryService mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new LaundryService(
                rs.getInt("id"),
                rs.getString("service_name"),
                PricingUnit.valueOf(rs.getString("pricing_unit")),
                rs.getBigDecimal("current_price"),
                rs.getBoolean("is_active"),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                stagesOf(rs));
    }

    /** Reads the needs_* columns as a stage list. */
    private static List<OrderStatus> stagesOf(ResultSet rs) throws SQLException {
        List<OrderStatus> stages = new ArrayList<>();
        if (rs.getBoolean("needs_washing")) {
            stages.add(OrderStatus.WASHING);
        }
        if (rs.getBoolean("needs_drying")) {
            stages.add(OrderStatus.DRYING);
        }
        if (rs.getBoolean("needs_ironing")) {
            stages.add(OrderStatus.IRONING);
        }
        return stages;
    }
}

package com.laundrylink.dao;

import com.laundrylink.model.LaundryService;
import com.laundrylink.model.PricingUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC queries for the services table. The caller supplies the connection so
 * several calls can share one transaction when write operations are added.
 */
public class LaundryServiceDAO {

    private static final String COLUMNS =
            "id, service_name, pricing_unit, current_price, is_active, created_at";

    /** Returns every service for the owner's management screen. */
    public List<LaundryService> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services"
                + " ORDER BY is_active DESC, service_name";
        return findMany(connection, sql);
    }

    /** Returns only services that may be selected for a new laundry order. */
    public List<LaundryService> findAllActive(Connection connection) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE is_active = 1"
                + " ORDER BY service_name";
        return findMany(connection, sql);
    }

    public LaundryService findById(Connection connection, int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM services WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private List<LaundryService> findMany(Connection connection, String sql) throws SQLException {
        List<LaundryService> services = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                services.add(mapRow(rs));
            }
        }
        return services;
    }

    private LaundryService mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new LaundryService(
                rs.getInt("id"),
                rs.getString("service_name"),
                PricingUnit.valueOf(rs.getString("pricing_unit")),
                rs.getBigDecimal("current_price"),
                rs.getBoolean("is_active"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}

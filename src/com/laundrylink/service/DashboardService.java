package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.DashboardDAO;
import com.laundrylink.model.DashboardStats;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.util.SessionContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily dashboard figures. Day boundaries use the shop's time zone (Asia/Manila).
 */
public class DashboardService {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Manila");

    private static final int RECENT_ORDER_LIMIT = 200;

    private final DashboardDAO dashboardDAO = new DashboardDAO();

    public static LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    public DashboardStats loadStats(LocalDate day) throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        if (day == null) {
            throw new ServiceException("Select a date.");
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.loadStats(connection, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        }
    }

    public List<OrderSummary> loadRecentOrders() throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.findRecentOrders(connection, RECENT_ORDER_LIMIT);
        }
    }

    public List<OrderItemLine> loadOrderItems(int orderId) throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.findOrderItems(connection, orderId);
        }
    }
}

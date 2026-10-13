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
 * Dashboard figures. Days follow Manila time.
 */
public class DashboardService {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Manila");

    private static final int RECENT_ORDER_LIMIT = 200;

    private final DashboardDAO dashboardDAO = new DashboardDAO();

    /** Today's date in Manila time. */
    public static LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    /** Dashboard figures for one day of the user's shop. */
    public DashboardStats loadStats(LocalDate day) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        if (day == null) {
            throw new ServiceException("Select a date.");
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.loadStats(connection, shopId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        }
    }

    /** The shop's most recent orders. */
    public List<OrderSummary> loadRecentOrders() throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.findRecentOrders(connection, shopId, RECENT_ORDER_LIMIT);
        }
    }

    /** The service lines of one order. */
    public List<OrderItemLine> loadOrderItems(int orderId) throws ServiceException, SQLException {
        int shopId = SessionContext.getCurrentShopId();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return dashboardDAO.findOrderItems(connection, shopId, orderId);
        }
    }
}

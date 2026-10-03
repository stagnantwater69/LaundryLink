package com.laundrylink.service;

import com.laundrylink.model.OrderStatus;
import com.laundrylink.util.SessionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Laundry order rules shared by all modules.
 *
 * releaseOrder is the single release method from the development plan:
 * Status Tracking and the Dashboard both call it; do not duplicate the rule in the UI.
 */
public class OrderService {

    /**
     * Releases an order to the customer. Requires READY_FOR_PICKUP and a zero
     * balance, re-checked with the order row locked, and records the RELEASED
     * history entry in the same transaction.
     */
    public void releaseOrder(int orderId) throws ServiceException, SQLException {
        int userId = SessionContext.getCurrentUserId();
        TransactionHelper.inTransaction(connection -> {
            OrderStatus status = lockOrderStatus(connection, orderId);
            if (status != OrderStatus.READY_FOR_PICKUP) {
                throw new ServiceException("Only orders that are Ready for Pickup can be released. "
                        + "This order is " + status.getDisplayName() + ".");
            }
            BigDecimal balance = currentBalance(connection, orderId);
            if (balance.signum() > 0) {
                throw new ServiceException("Full payment required before release.");
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE orders SET laundry_status = 'RELEASED' WHERE id = ?")) {
                update.setInt(1, orderId);
                update.executeUpdate();
            }
            try (PreparedStatement history = connection.prepareStatement(
                    "INSERT INTO order_status_history (order_id, changed_by, status)"
                    + " VALUES (?, ?, 'RELEASED')")) {
                history.setInt(1, orderId);
                history.setInt(2, userId);
                history.executeUpdate();
            }
            return null;
        });
    }

    private OrderStatus lockOrderStatus(Connection connection, int orderId) throws ServiceException, SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT laundry_status FROM orders WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException("That order no longer exists.");
                }
                return OrderStatus.valueOf(rs.getString(1));
            }
        }
    }

    private BigDecimal currentBalance(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM v_order_balances WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }
}

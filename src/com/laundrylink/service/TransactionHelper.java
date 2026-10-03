package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a unit of work in a single database transaction: commit on success,
 * rollback on any failure.
 */
public final class TransactionHelper {

    public interface Work<T> {
        T execute(Connection connection) throws SQLException, ServiceException;
    }

    private TransactionHelper() {
    }

    public static <T> T inTransaction(Work<T> work) throws SQLException, ServiceException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | ServiceException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }
}

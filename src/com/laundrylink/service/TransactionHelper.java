package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs several database writes as one transaction: all saved, or none.
 * The Services module uses it; the other services handle transactions directly.
 */
public final class TransactionHelper {

    /** The writes to run inside the transaction. */
    public interface Work<T> {
        T execute(Connection connection) throws SQLException, ServiceException;
    }

    private TransactionHelper() {
    }

    /** Commits if the work succeeds, rolls back if it fails. */
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

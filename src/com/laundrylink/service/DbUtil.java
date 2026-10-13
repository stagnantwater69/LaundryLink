package com.laundrylink.service;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Small helpers for finishing a transaction safely.
 */
public final class DbUtil {

    private DbUtil() {
    }

    /** Undoes the open transaction, ignoring errors. */
    public static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original error is the one that matters.
        }
    }

    /** Closes the connection, ignoring errors. */
    public static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing more to do.
        }
    }

    /** True if the database rejected a duplicate value (like a username). */
    public static boolean isDuplicateKey(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }
}

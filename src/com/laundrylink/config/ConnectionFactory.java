package com.laundrylink.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives out database connections and reuses them, since opening a new
 * MySQL connection is the slowest part of most queries.
 *
 * Use connections normally and close them when done; close() just puts
 * the connection back in a small pool.
 */
public final class ConnectionFactory {

    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final int MAX_IDLE_CONNECTIONS = 4;

    // Settings are read from the file the first time they're needed.
    private static DatabaseConfig config;

    // Open connections waiting to be reused (lock on IDLE).
    private static final List<Connection> IDLE = new ArrayList<Connection>();

    private ConnectionFactory() {
    }

    /** A ready connection: a reused one if available, otherwise a new one. */
    public static Connection getConnection() throws SQLException {
        Connection real = takeIdleConnection();
        if (real == null) {
            DatabaseConfig settings = getConfig();
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            real = DriverManager.getConnection(settings.getUrl(), settings.getUser(), settings.getPassword());
        }
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, new PooledConnection(real));
    }

    /** Reads the settings file once. */
    private static synchronized DatabaseConfig getConfig() {
        if (config == null) {
            config = DatabaseConfig.load();
        }
        return config;
    }

    /** Takes a working connection from the pool, or null if none are left. */
    private static Connection takeIdleConnection() {
        while (true) {
            Connection candidate;
            synchronized (IDLE) {
                if (IDLE.isEmpty()) {
                    return null;
                }
                candidate = IDLE.remove(IDLE.size() - 1);
            }
            try {
                // MySQL drops connections left idle too long; skip those.
                if (candidate.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                    return candidate;
                }
            } catch (SQLException ignored) {
                // Treat it as broken.
            }
            closeQuietly(candidate);
        }
    }

    /** Puts a connection back for reuse, undoing any unfinished transaction. */
    private static void giveBack(Connection real) {
        try {
            if (!real.getAutoCommit()) {
                real.rollback();            // drop anything not committed, like a real close would
                real.setAutoCommit(true);
            }
        } catch (SQLException e) {
            closeQuietly(real);
            return;
        }
        synchronized (IDLE) {
            if (IDLE.size() < MAX_IDLE_CONNECTIONS) {
                IDLE.add(real);
                return;
            }
        }
        closeQuietly(real);
    }

    /** Really closes a connection, ignoring errors. */
    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing more to do.
        }
    }

    /** Wraps a real connection. Everything passes through except close(), which returns it to the pool (once). */
    private static class PooledConnection implements InvocationHandler {
        private final Connection real;
        private boolean closed;

        PooledConnection(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("close")) {
                if (!closed) {
                    closed = true;
                    giveBack(real);
                }
                return null;
            }
            if (name.equals("isClosed")) {
                return closed || real.isClosed();
            }
            if (closed) {
                throw new SQLException("This connection was already closed.");
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}

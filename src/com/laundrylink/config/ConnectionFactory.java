package com.laundrylink.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens JDBC connections to the LaundryLink MySQL database.
 * Callers must close connections with try-with-resources.
 */
public final class ConnectionFactory {

    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private static volatile DatabaseConfig config;

    private ConnectionFactory() {
    }

    public static Connection getConnection() throws SQLException {
        DatabaseConfig settings = getConfig();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        return DriverManager.getConnection(settings.getUrl(), settings.getUser(), settings.getPassword());
    }

    private static DatabaseConfig getConfig() {
        DatabaseConfig current = config;
        if (current == null) {
            synchronized (ConnectionFactory.class) {
                current = config;
                if (current == null) {
                    current = DatabaseConfig.load();
                    config = current;
                }
            }
        }
        return current;
    }
}

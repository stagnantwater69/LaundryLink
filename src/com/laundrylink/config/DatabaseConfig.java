package com.laundrylink.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads the database URL, user and password from config/database.properties.
 * Environment variables LAUNDRYLINK_DB_URL, LAUNDRYLINK_DB_USER and
 * LAUNDRYLINK_DB_PASSWORD win if they're set.
 */
public final class DatabaseConfig {

    public static final String CONFIG_FILE = "config/database.properties";

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/laundrylink";
    private static final String DEFAULT_USER = "laundrylink_app";

    private final String url;
    private final String user;
    private final String password;

    /** Keeps the three settings. */
    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Loads the settings file (if there is one) and returns the settings. */
    public static DatabaseConfig load() {
        Properties properties = new Properties();
        File file = new File(System.getProperty("laundrylink.config", CONFIG_FILE));
        if (file.isFile()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read database settings from " + file.getAbsolutePath(), e);
            } finally {
                closeQuietly(in);
            }
        }

        String url = firstNonBlank(System.getenv("LAUNDRYLINK_DB_URL"), properties.getProperty("db.url"), DEFAULT_URL);
        String user = firstNonBlank(System.getenv("LAUNDRYLINK_DB_USER"), properties.getProperty("db.user"), DEFAULT_USER);
        String password = firstNonBlank(System.getenv("LAUNDRYLINK_DB_PASSWORD"), properties.getProperty("db.password"), "");
        return new DatabaseConfig(url.trim(), user.trim(), password);
    }

    /** The first of the three values that isn't blank. */
    private static String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return third == null ? "" : third;
    }

    /** Closes the settings file, ignoring close errors. */
    private static void closeQuietly(FileInputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing else to do here.
        }
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}

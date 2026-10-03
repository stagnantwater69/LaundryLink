package com.laundrylink.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Database connection settings.
 *
 * Read from config/database.properties (relative to the project/working folder),
 * with environment variables LAUNDRYLINK_DB_URL, LAUNDRYLINK_DB_USER and
 * LAUNDRYLINK_DB_PASSWORD taking priority when set.
 */
public final class DatabaseConfig {

    public static final String CONFIG_FILE = "config/database.properties";

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/laundrylink";
    private static final String DEFAULT_USER = "laundrylink_app";

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConfig load() {
        Properties properties = new Properties();
        Path path = Paths.get(System.getProperty("laundrylink.config", CONFIG_FILE));
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read database settings from " + path.toAbsolutePath(), e);
            }
        }

        String url = firstNonBlank(System.getenv("LAUNDRYLINK_DB_URL"), properties.getProperty("db.url"), DEFAULT_URL);
        String user = firstNonBlank(System.getenv("LAUNDRYLINK_DB_USER"), properties.getProperty("db.user"), DEFAULT_USER);
        String password = firstNonBlank(System.getenv("LAUNDRYLINK_DB_PASSWORD"), properties.getProperty("db.password"), "");
        return new DatabaseConfig(url.trim(), user.trim(), password);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
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

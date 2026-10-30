package com.lastplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection manages JDBC connections to the MySQL lastplate_db database.
 * Credentials are read securely from config.properties or environment variables.
 */
public class DBConnection {

    private static String dbUrl = "jdbc:mysql://localhost:3306/lastplate_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String dbUser = "root";
    private static String dbPassword = "root";

    static {
        loadConfiguration();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] MySQL JDBC Driver not found! Make sure mysql-connector-j is in classpath.");
        }
    }

    private static void loadConfiguration() {
        Properties props = new Properties();

        // Check for config.properties in working directory
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                props.load(input);
            } catch (Exception e) {
                System.err.println("[DBConnection] Could not read config.properties: " + e.getMessage());
            }
        } else {
            // Also check classpath
            try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (input != null) {
                    props.load(input);
                }
            } catch (Exception ignored) {
            }
        }

        // Apply config file values if present
        if (props.getProperty("db.url") != null && !props.getProperty("db.url").trim().isEmpty()) {
            dbUrl = props.getProperty("db.url").trim();
        }
        if (props.getProperty("db.user") != null && !props.getProperty("db.user").trim().isEmpty()) {
            dbUser = props.getProperty("db.user").trim();
        }
        if (props.getProperty("db.password") != null) {
            dbPassword = props.getProperty("db.password").trim();
        }

        // Environment variables override config file if present
        String envUrl = System.getenv("LASTPLATE_DB_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) dbUrl = envUrl.trim();

        String envUser = System.getenv("LASTPLATE_DB_USER");
        if (envUser != null && !envUser.trim().isEmpty()) dbUser = envUser.trim();

        String envPass = System.getenv("LASTPLATE_DB_PASSWORD");
        if (envPass != null) dbPassword = envPass.trim();
    }

    /**
     * Gets a new active connection to the database.
     * @return Connection
     * @throws SQLException on connection failure
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Tests if database connection is working.
     * @return true if successful, false otherwise
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBConnection] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    /**
     * Safely closes one or more closeable resources (Connection, Statement, ResultSet).
     */
    public static void close(AutoCloseable... closeables) {
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}


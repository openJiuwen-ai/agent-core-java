/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Database engine initialization, session management, and table lifecycle.
 * <p>
 * Mirrors Python's {@code engine} module in
 * {@code openjiuwen.agent_teams.tools.database.engine}.
 */
public class DatabaseEngine {

    private static final Logger logger = Logger.getLogger(DatabaseEngine.class.getName());

    private final DatabaseConfig config;
    private Connection connection;
    private boolean initialized;

    /**
     * Create DatabaseEngine with config.
     *
     * @param config Database configuration
     */
    public DatabaseEngine(DatabaseConfig config) {
        this.config = config;
        this.initialized = false;
    }

    /**
     * Initialize and configure the database engine.
     *
     * @return CompletableFuture with the initialized engine
     */
    public CompletableFuture<DatabaseEngine> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (config.getDbType()) {
                    case SQLITE:
                        initSQLite();
                        break;
                    case POSTGRESQL:
                        initPostgreSQL();
                        break;
                    case MYSQL:
                        initMySQL();
                        break;
                }
                initialized = true;
                logger.info("Database engine initialized: " + config.getDbType());
                return this;
            } catch (SQLException e) {
                logger.warning("Failed to initialize database: " + e.getMessage());
                throw new RuntimeException("Database initialization failed", e);
            }
        });
    }

    // ── Database-specific initialization ───────────────────────────────────────

    private void initSQLite() throws SQLException {
        String connStr = config.getConnectionString();
        boolean inMemory = ":memory:".equals(connStr);

        if (!inMemory) {
            // Ensure parent directory exists
            java.nio.file.Path dbPath = java.nio.file.Path.of(connStr);
            java.nio.file.Path parentDir = dbPath.getParent();
            if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
                try {
                    java.nio.file.Files.createDirectories(dbPath.getParent());
                } catch (Exception e) {
                    logger.warning("Failed to create database directory: " + e.getMessage());
                }
            }
        }

        // Connect to SQLite
        String jdbcUrl = inMemory ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + connStr;
        connection = DriverManager.getConnection(jdbcUrl);

        // Enable foreign keys
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        // Enable WAL mode for file-based databases
        if (config.isDbEnableWal() && !inMemory) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }
        }
    }

    private void initPostgreSQL() throws SQLException {
        String connStr = config.getConnectionString();
        if (connStr == null || connStr.isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL requires a non-empty connection_string");
        }

        // Convert to JDBC URL format
        String jdbcUrl = convertToJdbcUrl(connStr, "postgresql");
        connection = DriverManager.getConnection(jdbcUrl);
    }

    private void initMySQL() throws SQLException {
        String connStr = config.getConnectionString();
        if (connStr == null || connStr.isEmpty()) {
            throw new IllegalArgumentException("MySQL requires a non-empty connection_string");
        }

        // Convert to JDBC URL format
        String jdbcUrl = convertToJdbcUrl(connStr, "mysql");
        connection = DriverManager.getConnection(jdbcUrl);
    }

    private String convertToJdbcUrl(String connStr, String dbType) {
        // Convert SQLAlchemy-style URLs to JDBC URLs
        // postgresql+asyncpg://user:pass@host:port/db -> jdbc:postgresql://host:port/db?user=user&password=pass
        // mysql+aiomysql://user:pass@host:port/db -> jdbc:mysql://host:port/db?user=user&password=pass
        if (connStr.startsWith("jdbc:")) {
            return connStr;
        }
        // Simplified conversion - actual implementation would parse the URL
        return "jdbc:" + dbType + ":" + connStr.replace(dbType + "+asyncpg://", "")
            .replace(dbType + "+aiomysql://", "")
            .replace(dbType + "://", "");
    }

    // ── Session management ───────────────────────────────────────

    /**
     * Get the current connection.
     *
     * @return Connection or null if not initialized
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Check if engine is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Close the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.warning("Failed to close database: " + e.getMessage());
            }
            connection = null;
            initialized = false;
        }
    }

    // ── Table operations ───────────────────────────────────────

    /**
     * Drop a table if it exists.
     *
     * @param tableName Table name to drop
     */
    public void dropTable(String tableName) throws SQLException {
        String quotedName = tableName.replace("\"", "\"\"");
        try (var stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"" + quotedName + "\"");
        }
    }

    /**
     * Clear all rows from a table.
     *
     * @param tableName Table name to clear
     */
    public void clearTable(String tableName) throws SQLException {
        String quotedName = tableName.replace("\"", "\"\"");
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM \"" + quotedName + "\"");
        }
    }

    /**
     * Get all table names in the database.
     */
    public Set<String> getTableNames() throws SQLException {
        java.util.Set<String> tables = new java.util.HashSet<>();
        var meta = connection.getMetaData();
        var rs = meta.getTables(null, null, "%", new String[]{"TABLE"});
        while (rs.next()) {
            tables.add(rs.getString("TABLE_NAME"));
        }
        rs.close();
        return tables;
    }

    // ── Current time helper ───────────────────────────────────────

    /**
     * Return current time in milliseconds.
     */
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "DatabaseEngine{config=" + config + ", initialized=" + initialized + "}";
    }
}
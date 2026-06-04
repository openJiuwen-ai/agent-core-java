/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

/**
 * Database configuration class.
 * <p>
 * Mirrors Python's {@code DatabaseConfig} in
 * {@code openjiuwen.agent_teams.tools.database.config}.
 */
public class DatabaseConfig {

    private DatabaseType dbType;
    private String connectionString;
    private int dbTimeout;
    private boolean dbEnableWal;

    /**
     * Create default DatabaseConfig (SQLite, empty connection string).
     */
    public DatabaseConfig() {
        this.dbType = DatabaseType.SQLITE;
        this.connectionString = "";
        this.dbTimeout = 30;
        this.dbEnableWal = true;
    }

    /**
     * Create DatabaseConfig with specified parameters.
     *
     * @param dbType           Database type
     * @param connectionString Connection string
     * @param dbTimeout        Timeout in seconds
     * @param dbEnableWal      Enable WAL mode for SQLite
     */
    public DatabaseConfig(DatabaseType dbType, String connectionString, int dbTimeout, boolean dbEnableWal) {
        this.dbType = dbType;
        this.connectionString = connectionString != null ? connectionString : "";
        this.dbTimeout = dbTimeout;
        this.dbEnableWal = dbEnableWal;
    }

    // ── Getters and Setters ───────────────────────────────────────

    public DatabaseType getDbType() { return dbType; }
    public void setDbType(DatabaseType dbType) { this.dbType = dbType; }

    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }

    public int getDbTimeout() { return dbTimeout; }
    public void setDbTimeout(int dbTimeout) { this.dbTimeout = dbTimeout; }

    public boolean isDbEnableWal() { return dbEnableWal; }
    public void setDbEnableWal(boolean dbEnableWal) { this.dbEnableWal = dbEnableWal; }

    // ── Factory methods ───────────────────────────────────────

    /**
     * Create SQLite in-memory config.
     */
    public static DatabaseConfig inMemory() {
        return new DatabaseConfig(DatabaseType.SQLITE, ":memory:", 30, false);
    }

    /**
     * Create SQLite file-based config.
     */
    public static DatabaseConfig sqliteFile(String path) {
        return new DatabaseConfig(DatabaseType.SQLITE, path, 30, true);
    }

    /**
     * Create PostgreSQL config.
     */
    public static DatabaseConfig postgresql(String connectionString) {
        return new DatabaseConfig(DatabaseType.POSTGRESQL, connectionString, 30, false);
    }

    /**
     * Create MySQL config.
     */
    public static DatabaseConfig mysql(String connectionString) {
        return new DatabaseConfig(DatabaseType.MYSQL, connectionString, 30, false);
    }

    @Override
    public String toString() {
        return "DatabaseConfig{dbType=" + dbType + 
            ", connectionString='" + connectionString + "'" +
            ", dbTimeout=" + dbTimeout +
            ", dbEnableWal=" + dbEnableWal + "}";
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Database configuration class.
 * <p>
 * Mirrors Python's {@code DatabaseConfig} in
 * {@code openjiuwen/agent_teams/tools/database/config.py}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatabaseConfig {

    @Builder.Default
    @JsonProperty("db_type")
    private DatabaseType dbType = DatabaseType.SQLITE;

    @Builder.Default
    @JsonProperty("connection_string")
    private String connectionString = "";

    @Builder.Default
    @JsonProperty("db_timeout")
    private int dbTimeout = 30;

    @Builder.Default
    @JsonProperty("db_enable_wal")
    private boolean dbEnableWal = true;

    public DatabaseConfig() {
    }

    public DatabaseConfig(DatabaseType dbType, String connectionString, int dbTimeout, boolean dbEnableWal) {
        this.dbType = dbType == null ? DatabaseType.SQLITE : dbType;
        this.connectionString = connectionString == null ? "" : connectionString;
        this.dbTimeout = dbTimeout;
        this.dbEnableWal = dbEnableWal;
    }

    public static DatabaseConfig inMemory() {
        return new DatabaseConfig(DatabaseType.SQLITE, ":memory:", 30, false);
    }

    public static DatabaseConfig sqliteFile(String path) {
        return new DatabaseConfig(DatabaseType.SQLITE, path == null ? "" : path, 30, true);
    }

    public static DatabaseConfig postgresql(String connectionString) {
        return new DatabaseConfig(DatabaseType.POSTGRESQL, connectionString == null ? "" : connectionString, 30, false);
    }

    public static DatabaseConfig mysql(String connectionString) {
        return new DatabaseConfig(DatabaseType.MYSQL, connectionString == null ? "" : connectionString, 30, false);
    }

    public static DatabaseConfig fromStorageType(String storageType) {
        return fromStorageType(storageType, null);
    }

    public static DatabaseConfig fromStorageType(String storageType, String connectionString) {
        if (storageType == null || storageType.isBlank()) {
            return new DatabaseConfig();
        }
        return switch (storageType.toLowerCase()) {
            case "sqlite" -> sqliteFile(connectionString);
            case "memory" -> inMemory();
            case "postgresql" -> postgresql(connectionString);
            case "mysql" -> mysql(connectionString);
            default -> new DatabaseConfig();
        };
    }

    public DatabaseType getDbType() {
        return dbType;
    }

    public void setDbType(DatabaseType dbType) {
        this.dbType = dbType == null ? DatabaseType.SQLITE : dbType;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString == null ? "" : connectionString;
    }

    public int getDbTimeout() {
        return dbTimeout;
    }

    public void setDbTimeout(int dbTimeout) {
        this.dbTimeout = dbTimeout;
    }

    public boolean isDbEnableWal() {
        return dbEnableWal;
    }

    public void setDbEnableWal(boolean dbEnableWal) {
        this.dbEnableWal = dbEnableWal;
    }

    @Override
    public String toString() {
        return "DatabaseConfig{"
                + "dbType=" + dbType
                + ", connectionString='" + connectionString + '\''
                + ", dbTimeout=" + dbTimeout
                + ", dbEnableWal=" + dbEnableWal
                + '}';
    }
}

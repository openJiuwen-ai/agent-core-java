/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

/**
 * Minimal config for selecting the in-memory team storage backend.
 *
 * <p>Mirrors Python's {@code MemoryDatabaseConfig} in
 * {@code openjiuwen/agent_teams/tools/memory_database.py}.</p>
 */
public class MemoryDatabaseConfig {

    private String dbType = "memory";
    private String connectionString = "";

    public MemoryDatabaseConfig() {
    }

    public MemoryDatabaseConfig(String dbType, String connectionString) {
        this.dbType = dbType == null ? "memory" : dbType;
        this.connectionString = connectionString == null ? "" : connectionString;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType == null ? "memory" : dbType;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString == null ? "" : connectionString;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

/**
 * Supported database types.
 * <p>
 * Mirrors Python's {@code DatabaseType} in
 * {@code openjiuwen.agent_teams.tools.database.config}.
 */
public enum DatabaseType {
    SQLITE("sqlite"),
    POSTGRESQL("postgresql"),
    MYSQL("mysql");

    private final String value;

    DatabaseType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DatabaseType fromValue(String value) {
        for (DatabaseType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return SQLITE;  // Default
    }
}
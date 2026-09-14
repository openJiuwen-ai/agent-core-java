/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported database types.
 * <p>
 * Mirrors Python's {@code DatabaseType} in
 * {@code openjiuwen/agent_teams/tools/database/config.py}.
 */
public enum DatabaseType {
    SQLITE("sqlite"),
    POSTGRESQL("postgresql"),
    MYSQL("mysql");

    private final String value;

    DatabaseType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /**
     * Backward-compatible bean-style getter exposed by 0.1.12.
     *
     * @return serialized database type value
     */
    public String getValue() {
        return value();
    }

    @JsonCreator
    public static DatabaseType fromValue(String value) {
        if (value == null) {
            return SQLITE;
        }
        for (DatabaseType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return SQLITE;
    }
}

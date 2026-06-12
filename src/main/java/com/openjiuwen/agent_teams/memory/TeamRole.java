/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Team member role literal values.
 *
 * <p>Mirrors Python's {@code TeamRole} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
public enum TeamRole {
    LEADER("leader"),
    TEAMMATE("teammate");

    private final String value;

    TeamRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TeamRole fromValue(String value) {
        for (TeamRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown team role: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

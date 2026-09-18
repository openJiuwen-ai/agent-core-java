/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Strategy for handling concurrent shared-workspace modifications.
 *
 * <p>Mirrors Python's {@code ConflictStrategy} in
 * {@code openjiuwen/agent_teams/team_workspace/models.py}.</p>
 */
public enum ConflictStrategy {
    LOCK("lock"),
    MERGE("merge"),
    LAST_WRITE_WINS("last_write_wins");

    private final String value;

    ConflictStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ConflictStrategy fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ConflictStrategy strategy : values()) {
            if (strategy.value.equals(value)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown conflict strategy: " + value);
    }
}

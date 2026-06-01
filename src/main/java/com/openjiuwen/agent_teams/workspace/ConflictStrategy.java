/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

/**
 * Conflict strategy for team workspace changes.
 *
 * <p>Mirrors Python's {@code ConflictStrategy} in
 * {@code openjiuwen.agent_teams.team_workspace.models}.</p>
 */
public enum ConflictStrategy {
    LOCK("lock"),
    MERGE("merge"),
    LAST_WRITE_WINS("last_write_wins");

    private final String value;

    ConflictStrategy(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}

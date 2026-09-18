/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Team workspace operating mode.
 *
 * <p>Mirrors Python's {@code WorkspaceMode} in
 * {@code openjiuwen/agent_teams/team_workspace/models.py}.</p>
 */
public enum WorkspaceMode {
    LOCAL("local"),
    DISTRIBUTED("distributed");

    private final String value;

    WorkspaceMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static WorkspaceMode fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WorkspaceMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown workspace mode: " + value);
    }
}

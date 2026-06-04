/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

/**
 * Team workspace operating mode.
 *
 * <p>Mirrors Python's {@code WorkspaceMode} in
 * {@code openjiuwen.agent_teams.team_workspace.models}.</p>
 */
public enum WorkspaceMode {
    LOCAL("local"),
    DISTRIBUTED("distributed");

    private final String value;

    WorkspaceMode(String value) {
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

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

/**
 * Minimal team-tool output wrapper.
 *
 * <p>Mirrors Python's {@code ToolOutput} / mapped output usage in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public class TeamToolOutput {

    private final boolean success;
    private final Object data;
    private final String error;

    public TeamToolOutput(boolean success, Object data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        if (!success) {
            return error != null ? error : "Operation failed";
        }
        return data != null ? String.valueOf(data) : "OK";
    }
}

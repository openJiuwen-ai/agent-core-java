/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Outcome of dispatching a run request.
 *
 * <p>Mirrors Python's {@code RunActionKind} in
 * {@code openjiuwen/agent_teams/runtime/dispatch.py}.</p>
 */
public enum RunActionKind {
    CREATE("create"),
    NEW_TEAM_IN_SESSION("new_team_in_session"),
    COLD_RECOVER("cold_recover"),
    RESUME_FROM_PAUSE("resume_from_pause"),
    REJECT_RUNNING("reject_running"),
    REJECT_ORPHANED("reject_orphaned"),
    REJECT_INCONSISTENT("reject_inconsistent");

    private final String value;

    RunActionKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

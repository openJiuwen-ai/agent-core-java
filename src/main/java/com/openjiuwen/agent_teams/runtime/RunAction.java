/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Resolved action for a team run request.
 *
 * <p>Mirrors Python's {@code RunAction} dataclass in
 * {@code openjiuwen/agent_teams/runtime/dispatch.py}.</p>
 */
public record RunAction(RunActionKind kind, boolean requireSpec, String reason) {

    public RunAction(RunActionKind kind, boolean requireSpec) {
        this(kind, requireSpec, null);
    }
}

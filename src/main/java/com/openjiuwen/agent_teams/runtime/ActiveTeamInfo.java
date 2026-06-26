/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Read-only snapshot of a pooled team for external observers.
 *
 * <p>Mirrors Python's {@code ActiveTeamInfo} in
 * {@code openjiuwen/agent_teams/runtime/pool.py}.</p>
 */
public record ActiveTeamInfo(
        String teamName,
        String currentSessionId,
        RuntimeState state,
        boolean gateClosed
) {
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Resolved team runtime and the action that produced it.
 *
 * <p>Mirrors Python's {@code TeamRuntimeActivation} in
 * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
 */
public record TeamRuntimeActivation(
        TeamRuntimeManager.TeamAgentRuntime agent,
        TeamRuntimeManager.AgentTeamSessionView session,
        RunAction action
) {
}

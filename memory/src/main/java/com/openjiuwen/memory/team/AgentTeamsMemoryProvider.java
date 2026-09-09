/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.team;

import com.openjiuwen.agentteams.memory.TeamMemory;
import com.openjiuwen.agentteams.memory.TeamMemoryContext;
import com.openjiuwen.agentteams.memory.TeamMemoryProvider;

/**
 * Service provider for the Memory-backed Agent Teams integration.
 *
 * @since 0.1.7
 */
public final class AgentTeamsMemoryProvider implements TeamMemoryProvider {
    @Override
    public TeamMemory create(TeamMemoryContext context) {
        return new AgentTeamsMemory(context);
    }
}

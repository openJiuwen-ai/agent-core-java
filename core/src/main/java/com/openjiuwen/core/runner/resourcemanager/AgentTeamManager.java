/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Agent-team resource manager.
 *
 * <p>Mirrors Python's {@code AgentTeamMgr} in
 * {@code openjiuwen/core/runner/resources_manager/agent_team_manager.py}.</p>
 */
public class AgentTeamManager extends AbstractManager<Object> {

    public void addAgentTeam(String agentTeamId, Supplier<?> agentTeam) {
        registerResourceProvider(agentTeamId, agentTeam);
    }

    public Supplier<?> removeAgentTeam(String agentTeamId) {
        return unregisterResourceProvider(agentTeamId);
    }

    public CompletionStage<Object> getAgentTeam(String agentTeamId) {
        return getResource(agentTeamId);
    }
}

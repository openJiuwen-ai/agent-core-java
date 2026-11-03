/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;

/**
 * Legacy array-returning facade for hierarchical message-bus supervisor creation.
 *
 * <p>Mirrors Python's {@code SupervisorAgent} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/supervisor_agent.py}.</p>
 */
public final class SupervisorAgent {

    private SupervisorAgent() {
    }

    public static Object[] create(List<AgentCard> agents,
                                  ModelClientConfig modelClientConfig,
                                  ModelRequestConfig modelRequestConfig,
                                  AgentCard agentCard,
                                  String systemPrompt) {
        return create(agents, modelClientConfig, modelRequestConfig, agentCard, systemPrompt, 5, 10);
    }

    public static Object[] create(List<AgentCard> agents,
                                  ModelClientConfig modelClientConfig,
                                  ModelRequestConfig modelRequestConfig,
                                  AgentCard agentCard,
                                  String systemPrompt,
                                  int maxIterations,
                                  int maxParallelSubAgents) {
        com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.SupervisorAgent.CreatedSupervisor created =
                com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.SupervisorAgent.create(
                        agents,
                        modelClientConfig,
                        modelRequestConfig,
                        agentCard,
                        systemPrompt,
                        maxIterations,
                        maxParallelSubAgents
                );
        return new Object[] {created.agentCard(), created.provider()};
    }
}

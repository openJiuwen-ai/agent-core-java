/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.multi_agent.team_runtime.CommunicableAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Default LLM-driven supervisor for HierarchicalTeam.
 *
 * <p>Mirrors Python's {@code SupervisorAgent} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/supervisor_agent.py}.</p>
 */
public class SupervisorAgent extends ReActAgent implements CommunicableAgent {

    private P2PAbilityManager abilityManager;

    public SupervisorAgent(AgentCard card) {
        this(card, null, 10);
    }

    public SupervisorAgent(AgentCard card, ReActAgentConfig config) {
        this(card, config, 10);
    }

    public SupervisorAgent(AgentCard card, ReActAgentConfig config, int maxParallelSubAgents) {
        super(card);
        if (config != null) {
            super.configure(config);
        }
        this.abilityManager = new P2PAbilityManager(this, maxParallelSubAgents);
        this.abilityManager.setContextEngine(getContextEngine());
        setAbilityManager(this.abilityManager);
    }

    public static CreatedSupervisor create(List<AgentCard> agents,
                                           ModelClientConfig modelClientConfig,
                                           ModelRequestConfig modelRequestConfig,
                                           AgentCard agentCard,
                                           String systemPrompt) {
        return create(agents, modelClientConfig, modelRequestConfig, agentCard, systemPrompt, 5, 10);
    }

    public static CreatedSupervisor create(List<AgentCard> agents,
                                           ModelClientConfig modelClientConfig,
                                           ModelRequestConfig modelRequestConfig,
                                           AgentCard agentCard,
                                           String systemPrompt,
                                           int maxIterations,
                                           int maxParallelSubAgents) {
        if (agents == null || agents.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_CREATE_RUNTIME_ERROR,
                    "error_msg",
                    "[SupervisorAgent.create] agents list must not be empty"
            );
        }
        for (Object card : agents) {
            if (!(card instanceof AgentCard)) {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_CREATE_RUNTIME_ERROR,
                        "error_msg",
                        "[SupervisorAgent.create] each agents entry must be AgentCard, got "
                                + (card == null ? "null" : card.getClass().getName())
                );
            }
        }

        Supplier<SupervisorAgent> provider = () -> {
            ReActAgentConfig config = new ReActAgentConfig();
            config.setModelClientConfig(modelClientConfig);
            config.setModelConfigObj(modelRequestConfig);
            config.configureMaxIterations(maxIterations);
            config.configurePromptTemplate(List.of(new LinkedHashMap<>(Map.of(
                    "role", "system",
                    "content", systemPrompt == null ? "" : systemPrompt
            ))));

            if (modelClientConfig != null) {
                config.setModelProvider(modelClientConfig.getClientProvider());
                config.setApiKey(modelClientConfig.getApiKey());
                config.setApiBase(modelClientConfig.getApiBase());
            }
            if (modelRequestConfig != null && modelRequestConfig.getModelName() != null
                    && !modelRequestConfig.getModelName().isEmpty()) {
                config.setModelName(modelRequestConfig.getModelName());
            }

            SupervisorAgent supervisor = new SupervisorAgent(agentCard, config, maxParallelSubAgents);
            for (AgentCard card : agents) {
                supervisor.registerSubAgentCard(card);
                Loggers.MULTI_AGENT.debug("[SupervisorAgent.create] registered sub-agent card id={}",
                        card.getId());
            }
            Loggers.MULTI_AGENT.info("[SupervisorAgent.create] supervisor id={} sub_agents={} max_parallel_sub_agents={}",
                    agentCard.getId(), agents.stream().map(AgentCard::getId).toList(), maxParallelSubAgents);
            return supervisor;
        };
        return new CreatedSupervisor(agentCard, provider);
    }

    public void registerSubAgentCard(AgentCard card) {
        abilityManager.add(card);
        Loggers.MULTI_AGENT.debug("[{}] registered sub-agent '{}' (id={}) as LLM tool",
                getClass().getSimpleName(), card.getName(), card.getId());
    }

    public void register_sub_agent_card(AgentCard card) {
        registerSubAgentCard(card);
    }

    @Override
    public SupervisorAgent configure(Object config) {
        if (config instanceof ReActAgentConfig reactAgentConfig) {
            super.configure(reactAgentConfig);
            if (abilityManager != null) {
                abilityManager.setContextEngine(getContextEngine());
            }
        }
        return this;
    }

    public P2PAbilityManager getP2PAbilityManager() {
        return abilityManager;
    }

    public record CreatedSupervisor(AgentCard agentCard, Supplier<SupervisorAgent> provider) {
    }
}

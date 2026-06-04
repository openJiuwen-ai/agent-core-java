/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Default LLM-driven supervisor for HierarchicalTeam.
 * <p>
 * Mirrors Python's {@code SupervisorAgent} in
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.supervisor_agent}.
 * <p>
 * Combines CommunicableAgent (P2P send/publish) and ReActAgent execution.
 * AgentCard tool calls are routed via P2PAbilityManager; all other ability
 * types execute normally.
 */
public class SupervisorAgent extends ReActAgent implements CommunicableAgent {

    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;

    private final P2PAbilityManager abilityManager;

    private TeamRuntime runtime;
    private String agentId;

    public SupervisorAgent(AgentCard card, int maxParallelSubAgents) {
        super(card);
        this.abilityManager = new P2PAbilityManager(this, maxParallelSubAgents);
    }

    public SupervisorAgent(AgentCard card, ReActAgentConfig config, int maxParallelSubAgents) {
        this(card, maxParallelSubAgents);
        if (config != null) {
            super.configure(config);
        }
    }

    public SupervisorAgent(AgentCard card) {
        this(card, 10);
    }

    public static Object[] create(
            List<?> agents,
            ModelClientConfig modelClientConfig,
            ModelRequestConfig modelRequestConfig,
            AgentCard agentCard,
            String systemPrompt
    ) {
        return create(agents, modelClientConfig, modelRequestConfig, agentCard, systemPrompt, 5, 10);
    }

    /**
     * Create a SupervisorAgent pre-loaded with sub-agent cards.
     *
     * @return an Object array containing (AgentCard, Supplier&lt;SupervisorAgent&gt;)
     */
    public static Object[] create(
            List<?> agents,
            ModelClientConfig modelClientConfig,
            ModelRequestConfig modelRequestConfig,
            AgentCard agentCard,
            String systemPrompt,
            int maxIterations,
            int maxParallelSubAgents
    ) {
        if (agents == null || agents.isEmpty()) {
            ErrorHelper.raiseError(StatusCode.AGENT_GROUP_CREATE_RUNTIME_ERROR,
                    "[SupervisorAgent.create] agents list must not be empty", null, null, null);
        }

        List<AgentCard> cards = new ArrayList<>();
        for (Object item : agents) {
            if (!(item instanceof AgentCard)) {
                ErrorHelper.raiseError(StatusCode.AGENT_GROUP_CREATE_RUNTIME_ERROR,
                        "[SupervisorAgent.create] each agents entry must be AgentCard, got "
                                + (item == null ? "null" : item.getClass().getName()), null, null, null);
            }
            cards.add((AgentCard) item);
        }

        Supplier<SupervisorAgent> provider = () -> {
            ReActAgentConfig cfg = new ReActAgentConfig();
            cfg.setModelClientConfig(modelClientConfig);
            cfg.setModelConfigObj(modelRequestConfig);
            cfg.configureMaxIterations(maxIterations);
            cfg.configurePromptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)));
            if (modelClientConfig != null) {
                cfg.setModelProvider(modelClientConfig.getClientProvider());
                cfg.setApiKey(modelClientConfig.getApiKey());
                cfg.setApiBase(modelClientConfig.getApiBase());
            }
            if (modelRequestConfig != null && modelRequestConfig.getModelName() != null) {
                cfg.setModelName(modelRequestConfig.getModelName());
            }

            SupervisorAgent supervisor = new SupervisorAgent(agentCard, cfg, maxParallelSubAgents);
            for (AgentCard card : cards) {
                supervisor.registerSubAgentCard(card);
                LOGGER.debug("[SupervisorAgent.create] registered sub-agent card id={}", card.getId());
            }
            LOGGER.info("[SupervisorAgent.create] supervisor id={} sub_agents={} max_parallel_sub_agents={}",
                    agentCard.getId(), cards.stream().map(AgentCard::getId).toList(), maxParallelSubAgents);
            return supervisor;
        };
        return new Object[]{agentCard, provider};
    }

    public static Object create(AgentCard agentCard, int maxParallelSubAgents) {
        Supplier<SupervisorAgent> provider = () -> new SupervisorAgent(agentCard, maxParallelSubAgents);
        return new Object[]{agentCard, provider};
    }

    @Override
    public void bindRuntime(TeamRuntime runtime, String agentId) {
        this.runtime = runtime;
        this.agentId = agentId;
    }

    @Override
    public boolean isBound() {
        return runtime != null && agentId != null;
    }

    @Override
    public CompletableFuture<Object> send(Object message, String recipient, String sessionId) {
        return send(message, recipient, sessionId, null);
    }

    @Override
    public CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Supervisor not bound to runtime"));
        }
        return runtime.getMessageBus().send(
                message,
                recipient,
                Optional.ofNullable(agentId),
                Optional.ofNullable(sessionId),
                Optional.ofNullable(timeout)
        );
    }

    @Override
    public CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Supervisor not bound to runtime"));
        }
        return runtime.getMessageBus().publish(
                message,
                topicId,
                Optional.ofNullable(agentId),
                Optional.ofNullable(sessionId)
        );
    }

    @Override
    public void subscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().subscribe(agentId, topicPattern);
        }
    }

    @Override
    public void unsubscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().unsubscribe(agentId, topicPattern);
        }
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public TeamRuntime getRuntime() {
        return runtime;
    }

    public void registerSubAgentCard(AgentCard card) {
        abilityManager.add(card);
        LOGGER.debug("[SupervisorAgent] registered sub-agent '{}' (id={}) as LLM tool",
                card.getName(), card.getId());
    }

    public void register_sub_agent_card(AgentCard card) {
        registerSubAgentCard(card);
    }

    @Override
    public SupervisorAgent configure(Object config) {
        if (config instanceof ReActAgentConfig reactConfig) {
            super.configure(reactConfig);
        }
        return this;
    }

    @Override
    public P2PAbilityManager getAbilityManager() {
        return abilityManager;
    }
}

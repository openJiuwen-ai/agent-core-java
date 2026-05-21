/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.AbilityManager;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Default LLM-driven supervisor for HierarchicalTeam.
 * <p>
 * Mirrors Python's {@code SupervisorAgent} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.supervisor_agent}.
 * <p>
 * Combines CommunicableAgent (P2P send/publish) and agent execution.
 * AgentCard tool calls are routed via P2PAbilityManager;
 * all other ability types execute normally.
 */
public class SupervisorAgent implements CommunicableAgent {
    
    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;
    
    private final AgentCard card;
    private final P2PAbilityManager abilityManager;
    
    // CommunicableAgent state
    private TeamRuntime runtime;
    private String agentId;
    
    /**
     * Create a SupervisorAgent.
     * 
     * @param card AgentCard for this supervisor
     * @param maxParallelSubAgents Max concurrent sub-agent dispatches
     */
    public SupervisorAgent(AgentCard card, int maxParallelSubAgents) {
        this.card = card;
        this.abilityManager = new P2PAbilityManager(this, maxParallelSubAgents);
    }
    
    /**
     * Create a SupervisorAgent with default parallel limit.
     * 
     * @param card AgentCard for this supervisor
     */
    public SupervisorAgent(AgentCard card) {
        this(card, 10);
    }
    
    /**
     * Create a SupervisorAgent pre-loaded with sub-agent cards.
     * 
     * @param agentCard AgentCard for this supervisor
     * @param maxParallelSubAgents Max concurrent sub-agent dispatches
     * @return Tuple of (card, provider)
     */
    public static Object create(AgentCard agentCard, int maxParallelSubAgents) {
        Supplier<SupervisorAgent> provider = () -> new SupervisorAgent(agentCard, maxParallelSubAgents);
        return new Object[]{agentCard, provider};
    }
    
    // ========== CommunicableAgent Implementation ==========
    
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
        if (runtime == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Supervisor not bound to runtime"));
        }
        return runtime.getMessageBus().send(
            message, recipient,
            Optional.ofNullable(agentId),
            Optional.ofNullable(sessionId),
            Optional.empty()
        );
    }
    
    @Override
    public CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Supervisor not bound to runtime"));
        }
        return runtime.getMessageBus().publish(
            message, topicId,
            Optional.ofNullable(agentId),
            Optional.ofNullable(sessionId)
        );
    }
    
    @Override
    public void subscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().subscribe(topicPattern, agentId);
        }
    }
    
    @Override
    public void unsubscribe(String topicPattern) {
        if (runtime != null) {
            runtime.getSubscriptionManager().unsubscribe(topicPattern, agentId);
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
    
    // ========== Getters ==========
    
    public AgentCard getCard() {
        return card;
    }
    
    public P2PAbilityManager getAbilityManager() {
        return abilityManager;
    }
}
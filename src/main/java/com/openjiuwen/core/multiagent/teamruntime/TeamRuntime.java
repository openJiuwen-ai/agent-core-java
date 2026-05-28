/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Self-contained runtime for multi-agent communication.
 * <p>
 * Mirrors Python's {@code TeamRuntime} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.team_runtime}.
 * <p>
 * Manages agent registration, routes P2P and Pub-Sub messages.
 */
public class TeamRuntime {
    
    private final RuntimeConfig config;
    private final String teamId;
    private final SubscriptionManager subscriptionManager;
    
    /** Agent card registry */
    private final ConcurrentHashMap<String, AgentCard> agentCards = new ConcurrentHashMap<>();
    
    /** Agent provider registry */
    private final ConcurrentHashMap<String, Supplier<?>> agentProviders = new ConcurrentHashMap<>();
    
    /** P2P timeout in seconds */
    private double p2pTimeout;
    
    public TeamRuntime() {
        this(new RuntimeConfig());
    }
    
    public TeamRuntime(RuntimeConfig config) {
        this.config = config;
        this.teamId = config.getTeamId();
        this.subscriptionManager = new SubscriptionManager();
        this.p2pTimeout = config.getP2pTimeout();
    }
    
    /**
     * Register an agent with its card and provider.
     * 
     * @param card AgentCard
     * @param provider Supplier that creates agent instance
     */
    public void registerAgent(AgentCard card, Supplier<?> provider) {
        agentCards.put(card.getId(), card);
        agentProviders.put(card.getId(), provider);
    }
    
    /**
     * Check if an agent is registered.
     * 
     * @param agentId Agent ID
     * @return true if registered
     */
    public boolean hasAgent(String agentId) {
        return agentCards.containsKey(agentId);
    }
    
    /**
     * Get an agent card by ID.
     * 
     * @param agentId Agent ID
     * @return AgentCard or null
     */
    public AgentCard getAgentCard(String agentId) {
        return agentCards.get(agentId);
    }
    
    /**
     * Get all registered agent IDs.
     * 
     * @return Set of agent IDs
     */
    public Set<String> getAgentIds() {
        return agentCards.keySet();
    }
    
    /**
     * Set P2P timeout.
     * 
     * @param timeout Timeout in seconds
     */
    public void setP2pTimeout(double timeout) {
        this.p2pTimeout = timeout;
    }
    
    /**
     * Get subscription manager.
     * 
     * @return SubscriptionManager
     */
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }
    
    /**
     * Get message bus (creates one if needed).
     * 
     * @return MessageBus
     */
    public MessageBus getMessageBus() {
        // Lazy create message bus if needed
        if (messageBus == null) {
            MessageBusConfig busConfig = MessageBusConfig.builder()
                    .teamId(teamId)
                    .build();
            messageBus = new MessageBus(busConfig, this);
        }
        return messageBus;
    }
    
    private MessageBus messageBus;
    
    /**
     * Get team ID.
     * 
     * @return team ID
     */
    public String getTeamId() {
        return teamId;
    }
    
    /**
     * Unregister an agent.
     * 
     * @param agentId Agent ID
     */
    public void unregisterAgent(String agentId) {
        agentCards.remove(agentId);
        agentProviders.remove(agentId);
        subscriptionManager.unsubscribeAll(agentId);
    }
}
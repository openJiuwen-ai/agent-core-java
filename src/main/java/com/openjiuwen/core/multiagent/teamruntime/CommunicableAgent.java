/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Interface that adds messaging capabilities to an agent.
 * <p>
 * Mirrors Python's {@code CommunicableAgent} mixin in 
 * {@code openjiuwen.core.multi_agent.team_runtime.communicable_agent}.
 * <p>
 * Enables agents to send P2P messages, publish to topics, and manage
 * subscriptions through a bound TeamRuntime.
 */
public interface CommunicableAgent {

    Map<CommunicableAgent, Binding> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    record Binding(TeamRuntime runtime, String agentId) {
    }
    
    /**
     * Bind a TeamRuntime to this agent.
     * 
     * @param runtime TeamRuntime instance
     * @param agentId This agent's ID
     */
    default void bindRuntime(TeamRuntime runtime, String agentId) {
        Binding existing = BINDINGS.get(this);
        if (existing != null
                && existing.runtime() == runtime
                && java.util.Objects.equals(existing.agentId(), agentId)) {
            return;
        }
        BINDINGS.put(this, new Binding(runtime, agentId));
    }
    
    /**
     * Check whether this agent is bound to a runtime.
     * 
     * @return true if bound
     */
    default boolean isBound() {
        Binding binding = BINDINGS.get(this);
        return binding != null && binding.runtime() != null && binding.agentId() != null;
    }
    
    /**
     * Send a P2P message to a specific recipient.
     * 
     * @param message Message payload
     * @param recipient Recipient agent ID
     * @param sessionId Optional session ID
     * @return CompletableFuture with response
     */
    default CompletableFuture<Object> send(Object message, String recipient, String sessionId) {
        return getRuntime().send(message, recipient, getAgentId(), sessionId);
    }

    /**
     * Send a P2P message to a specific recipient with an optional timeout.
     *
     * @param message Message payload
     * @param recipient Recipient agent ID
     * @param sessionId Optional session ID
     * @param timeout Optional timeout in seconds
     * @return CompletableFuture with response
     */
    default CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
        return getRuntime().send(message, recipient, getAgentId(), sessionId, timeout);
    }
    
    /**
     * Publish a message to a topic.
     * 
     * @param message Message payload
     * @param topicId Topic ID
     * @param sessionId Optional session ID
     * @return CompletableFuture completed when all recipients receive
     */
    default CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        return getRuntime().publish(message, topicId, getAgentId(), sessionId);
    }
    
    /**
     * Subscribe to a topic pattern.
     * 
     * @param topicPattern Topic pattern (supports wildcards)
     */
    default void subscribe(String topicPattern) {
        getRuntime().subscribe(getAgentId(), topicPattern);
    }
    
    /**
     * Unsubscribe from a topic pattern.
     * 
     * @param topicPattern Topic pattern
     */
    default void unsubscribe(String topicPattern) {
        getRuntime().unsubscribe(getAgentId(), topicPattern);
    }
    
    /**
     * Get the agent ID.
     * 
     * @return agent ID or null if not bound
     */
    default String getAgentId() {
        Binding binding = BINDINGS.get(this);
        if (binding == null || binding.agentId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "Agent not bound to a TeamRuntime. Register the agent with a TeamRuntime first.",
                    null, null, null);
        }
        return binding.agentId();
    }
    
    /**
     * Get the bound runtime.
     * 
     * @return TeamRuntime or null if not bound
     */
    default TeamRuntime getRuntime() {
        Binding binding = BINDINGS.get(this);
        if (binding == null || binding.runtime() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "Agent not bound to a TeamRuntime. Register the agent with a TeamRuntime first.",
                    null, null, null);
        }
        return binding.runtime();
    }
}

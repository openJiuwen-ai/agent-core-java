/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

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
    
    /**
     * Bind a TeamRuntime to this agent.
     * 
     * @param runtime TeamRuntime instance
     * @param agentId This agent's ID
     */
    void bindRuntime(TeamRuntime runtime, String agentId);
    
    /**
     * Check whether this agent is bound to a runtime.
     * 
     * @return true if bound
     */
    boolean isBound();
    
    /**
     * Send a P2P message to a specific recipient.
     * 
     * @param message Message payload
     * @param recipient Recipient agent ID
     * @param sessionId Optional session ID
     * @return CompletableFuture with response
     */
    CompletableFuture<Object> send(Object message, String recipient, String sessionId);
    
    /**
     * Publish a message to a topic.
     * 
     * @param message Message payload
     * @param topicId Topic ID
     * @param sessionId Optional session ID
     * @return CompletableFuture completed when all recipients receive
     */
    CompletableFuture<Void> publish(Object message, String topicId, String sessionId);
    
    /**
     * Subscribe to a topic pattern.
     * 
     * @param topicPattern Topic pattern (supports wildcards)
     */
    void subscribe(String topicPattern);
    
    /**
     * Unsubscribe from a topic pattern.
     * 
     * @param topicPattern Topic pattern
     */
    void unsubscribe(String topicPattern);
    
    /**
     * Get the agent ID.
     * 
     * @return agent ID or null if not bound
     */
    String getAgentId();
    
    /**
     * Get the bound runtime.
     * 
     * @return TeamRuntime or null if not bound
     */
    TeamRuntime getRuntime();
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.HashSet;

/**
 * Routes P2P and Pub-Sub messages to agents.
 * <p>
 * Mirrors Python's {@code MessageRouter} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.message_router}.
 * <p>
 * Supports both point-to-point and fan-out patterns.
 */
public class MessageRouter {
    
    private final SubscriptionManager subscriptionManager;
    private TeamRuntime runtime;
    
    public MessageRouter(SubscriptionManager subscriptionManager, TeamRuntime runtime) {
        this.subscriptionManager = subscriptionManager;
        this.runtime = runtime;
    }
    
    public void setRuntime(TeamRuntime runtime) {
        this.runtime = runtime;
    }
    
    /**
     * Route a P2P message to recipient.
     * 
     * @param envelope Message envelope
     * @return CompletableFuture with response
     */
    public CompletableFuture<Object> routeP2pMessage(MessageEnvelope envelope) {
        String recipient = envelope.getRecipient().orElse(null);
        if (recipient == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("P2P message requires recipient"));
        }
        
        if (runtime == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("MessageRouter requires a TeamRuntime for P2P routing"));
        }
        return CompletableFuture.supplyAsync(() -> runtime.dispatchToAgent(
                recipient,
                envelope.getMessage(),
                envelope.getSessionId().orElse(null)
        ));
    }
    
    /**
     * Route a Pub-Sub message to all subscribers.
     * 
     * @param envelope Message envelope with topicId
     * @return CompletableFuture completed when all deliveries finish
     */
    public CompletableFuture<Void> routePubsubMessage(MessageEnvelope envelope) {
        String topicId = envelope.getTopicId().orElse(null);
        if (topicId == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Pub-Sub message requires topicId"));
        }
        
        Set<String> subscribers = subscriptionManager.getSubscribers(topicId);
        Set<CompletableFuture<Void>> futures = new HashSet<>();
        
        for (String agentId : subscribers) {
            CompletableFuture<Void> delivery = CompletableFuture.runAsync(() -> {
                if (runtime == null) {
                    throw new IllegalStateException("MessageRouter requires a TeamRuntime for Pub-Sub routing");
                }
                runtime.dispatchToAgent(agentId, envelope.getMessage(), envelope.getSessionId().orElse(null));
            });
            futures.add(delivery);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}

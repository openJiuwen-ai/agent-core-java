/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import java.util.concurrent.CompletableFuture;

/**
 * Message router for routing messages via runner.
 * <p>
 * Mirrors Python's {@code MessageRouter} class from
 * <code>multi_agent/team_runtime/message_router.py</code>.
 */
public class MessageRouter {

    private final Object runner;

    public MessageRouter(Object runner) {
        this.runner = runner;
    }

    /**
     * Route a P2P message to the recipient agent.
     */
    public CompletableFuture<Void> routeP2PMessage(MessageEnvelope envelope) {
        // In Java implementation, we would call runner.run_agent
        // This is a placeholder that returns completed future
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Route a pub-sub message to all subscribers.
     */
    public CompletableFuture<Void> routePubSubMessage(MessageEnvelope envelope, 
            SubscriptionManager subscriptionManager) {
        // Get all subscribers for the topic
        java.util.Set<String> subscribers = subscriptionManager.getSubscribers(
            envelope.getTopicId());
        
        // Route to each subscriber
        java.util.List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (String subscriberId : subscribers) {
            MessageEnvelope subEnvelope = new MessageEnvelope(
                envelope.getMessageId(),
                envelope.getMessage(),
                envelope.getSender(),
                subscriberId,
                envelope.getTopicId(),
                envelope.getSessionId()
            );
            futures.add(routeP2PMessage(subEnvelope));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public Object getRunner() { return runner; }
}
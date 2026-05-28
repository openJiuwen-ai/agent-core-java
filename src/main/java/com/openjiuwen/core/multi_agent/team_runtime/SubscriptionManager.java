/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.team_runtime;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscription manager for pub-sub topics.
 * <p>
 * Mirrors Python's {@code SubscriptionManager} class from
 * <code>multi_agent/team_runtime/subscription_manager.py</code>.
 */
public class SubscriptionManager {

    private final ConcurrentHashMap<String, Set<String>> topicSubscriptions = 
        new ConcurrentHashMap<>();

    /**
     * Subscribe an agent to a topic.
     */
    public void subscribe(String topic, String agentId) {
        topicSubscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
            .add(agentId);
    }

    /**
     * Unsubscribe an agent from a topic.
     */
    public void unsubscribe(String topic, String agentId) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.remove(agentId);
            if (subscribers.isEmpty()) {
                topicSubscriptions.remove(topic);
            }
        }
    }

    /**
     * Get all subscribers for a topic.
     */
    public Set<String> getSubscribers(String topic) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        return subscribers != null ? new HashSet<>(subscribers) : new HashSet<>();
    }

    /**
     * Check if an agent is subscribed to a topic.
     */
    public boolean isSubscribed(String topic, String agentId) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        return subscribers != null && subscribers.contains(agentId);
    }

    /**
     * Get all topics an agent is subscribed to.
     */
    public Set<String> getAgentTopics(String agentId) {
        Set<String> topics = new HashSet<>();
        for (String topic : topicSubscriptions.keySet()) {
            if (isSubscribed(topic, agentId)) {
                topics.add(topic);
            }
        }
        return topics;
    }

    /**
     * Clear all subscriptions.
     */
    public void clearAll() {
        topicSubscriptions.clear();
    }
}
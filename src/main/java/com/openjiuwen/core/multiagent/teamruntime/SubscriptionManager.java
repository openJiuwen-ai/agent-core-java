/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teamruntime;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages topic-to-agent subscriptions with wildcard matching.
 * <p>
 * Mirrors Python's {@code SubscriptionManager} in 
 * {@code openjiuwen.core.multi_agent.team_runtime.subscription_manager}.
 * <p>
 * Maintains bidirectional indices for efficient lookup and removal.
 * Supports exact and wildcard patterns.
 */
public class SubscriptionManager {
    
    /** Topic pattern -> agent IDs */
    private final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    
    /** Agent ID -> topic patterns */
    private final ConcurrentHashMap<String, Set<String>> agentTopics = new ConcurrentHashMap<>();
    
    /**
     * Subscribe an agent to a topic pattern.
     * 
     * @param agentId Agent ID
     * @param topicPattern Topic pattern
     */
    public void subscribe(String agentId, String topicPattern) {
        subscriptions.computeIfAbsent(topicPattern, k -> ConcurrentHashMap.newKeySet()).add(agentId);
        agentTopics.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(topicPattern);
    }
    
    /**
     * Unsubscribe an agent from a topic pattern.
     * 
     * @param agentId Agent ID
     * @param topicPattern Topic pattern
     */
    public void unsubscribe(String agentId, String topicPattern) {
        Set<String> agents = subscriptions.get(topicPattern);
        if (agents != null) {
            agents.remove(agentId);
            if (agents.isEmpty()) {
                subscriptions.remove(topicPattern);
            }
        }
        
        Set<String> topics = agentTopics.get(agentId);
        if (topics != null) {
            topics.remove(topicPattern);
            if (topics.isEmpty()) {
                agentTopics.remove(agentId);
            }
        }
    }
    
    /**
     * Remove all subscriptions for an agent.
     * 
     * @param agentId Agent ID
     */
    public void unsubscribeAll(String agentId) {
        Set<String> topics = agentTopics.get(agentId);
        if (topics == null) return;
        
        for (String topic : topics) {
            Set<String> agents = subscriptions.get(topic);
            if (agents != null) {
                agents.remove(agentId);
                if (agents.isEmpty()) {
                    subscriptions.remove(topic);
                }
            }
        }
        agentTopics.remove(agentId);
    }
    
    /**
     * Get all agent IDs subscribed to a topic (matching wildcards).
     * 
     * @param topic Topic to match
     * @return Set of matching agent IDs
     */
    public Set<String> getSubscribers(String topic) {
        Set<String> result = new HashSet<>();
        
        for (String pattern : subscriptions.keySet()) {
            if (matchesPattern(topic, pattern)) {
                result.addAll(subscriptions.get(pattern));
            }
        }
        
        return result;
    }
    
    /**
     * Get all topic patterns an agent is subscribed to.
     * 
     * @param agentId Agent ID
     * @return Set of topic patterns
     */
    public Set<String> getAgentTopics(String agentId) {
        return new HashSet<>(agentTopics.getOrDefault(agentId, Set.of()));
    }
    
    /**
     * Check if a topic matches a pattern (supports * and ? wildcards).
     * 
     * @param topic Topic string
     * @param pattern Pattern with wildcards
     * @return true if matches
     */
    private boolean matchesPattern(String topic, String pattern) {
        if (pattern.equals(topic)) return true;
        
        // Convert wildcard pattern to regex
        String regex = pattern.replace(".", "\\.")
                               .replace("*", ".*")
                               .replace("?", ".");
        return Pattern.matches(regex, topic);
    }
}
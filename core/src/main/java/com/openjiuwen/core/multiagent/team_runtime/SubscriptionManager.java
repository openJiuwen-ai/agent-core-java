/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manages topic-to-agent subscriptions with wildcard matching.
 *
 * <p>Mirrors Python's {@code SubscriptionManager} in
 * {@code openjiuwen/core/multi_agent/team_runtime/subscription_manager.py}.</p>
 */
public class SubscriptionManager {

    private final Map<String, Set<String>> subscriptions = new LinkedHashMap<>();

    private final Map<String, Set<String>> agentTopics = new LinkedHashMap<>();

    public void subscribe(String agentId, String topicPattern) {
        subscriptions.computeIfAbsent(topicPattern, ignored -> new LinkedHashSet<>()).add(agentId);
        agentTopics.computeIfAbsent(agentId, ignored -> new LinkedHashSet<>()).add(topicPattern);

        Loggers.MULTI_AGENT.debug("[{}] {} subscribed to {}", getClass().getSimpleName(), agentId, topicPattern);
    }

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

        Loggers.MULTI_AGENT.debug("[{}] {} unsubscribed from {}", getClass().getSimpleName(), agentId, topicPattern);
    }

    public void unsubscribeAll(String agentId) {
        Set<String> topics = agentTopics.get(agentId);
        if (topics == null) {
            return;
        }

        for (String topic : new ArrayList<>(topics)) {
            Set<String> agents = subscriptions.get(topic);
            if (agents != null) {
                agents.remove(agentId);
                if (agents.isEmpty()) {
                    subscriptions.remove(topic);
                }
            }
        }
        agentTopics.remove(agentId);

        Loggers.MULTI_AGENT.debug("[{}] Removed all subscriptions for {}", getClass().getSimpleName(), agentId);
    }

    public List<String> getSubscribers(String topicId) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
            if (matchPattern(topicId, entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }

        Loggers.MULTI_AGENT.debug("[{}] Found {} subscribers for: {}", getClass().getSimpleName(), result.size(), topicId);
        return new ArrayList<>(result);
    }

    public int getSubscriptionCount() {
        return subscriptions.values().stream().mapToInt(Set::size).sum();
    }

    public Map<String, Object> listSubscriptions() {
        return listSubscriptions(null);
    }

    public Map<String, Object> listSubscriptions(String agentId) {
        if (agentId != null) {
            return Map.of(
                    "agent_id", agentId,
                    "topics", new ArrayList<>(agentTopics.getOrDefault(agentId, Set.of()))
            );
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return Map.of("subscriptions", result);
    }

    static boolean matchPattern(String topicId, String pattern) {
        if (pattern.equals(topicId)) {
            return true;
        }
        if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
            return Pattern.matches(toRegex(pattern), topicId);
        }
        return false;
    }

    private static String toRegex(String pattern) {
        StringBuilder builder = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char current = pattern.charAt(index);
            switch (current) {
                case '*' -> builder.append(".*");
                case '?' -> builder.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> builder.append('\\').append(current);
                default -> builder.append(current);
            }
        }
        builder.append('$');
        return builder.toString();
    }
}

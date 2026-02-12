// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event-driven group card with subscription information.
 * 
 * <p>Extends GroupCard with subscription mapping for event-driven
 * message routing.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/multi_agent/schema/group_card.py}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class EventDrivenGroupCard extends GroupCard {
    
    /**
     * Subscription mapping: {agent_id: [topic1, topic2, ...]}.
     */
    private Map<String, List<String>> subscriptions;
    
    /**
     * Creates an empty event-driven group card.
     */
    public EventDrivenGroupCard() {
        super();
        this.subscriptions = new HashMap<>();
    }
    
    /**
     * Creates an event-driven group card with the specified name.
     *
     * @param name the group name
     */
    public EventDrivenGroupCard(String name) {
        super(name);
        this.subscriptions = new HashMap<>();
    }
    
    /**
     * Creates an event-driven group card with the specified name and description.
     *
     * @param name the group name
     * @param description the group description
     */
    public EventDrivenGroupCard(String name, String description) {
        super(name, description);
        this.subscriptions = new HashMap<>();
    }
    
    /**
     * Gets the subscriptions map.
     *
     * @return the subscriptions map
     */
    public Map<String, List<String>> getSubscriptions() {
        return subscriptions;
    }
    
    /**
     * Sets the subscriptions map.
     *
     * @param subscriptions the subscriptions map
     */
    public void setSubscriptions(Map<String, List<String>> subscriptions) {
        this.subscriptions = subscriptions != null ? subscriptions : new HashMap<>();
    }
    
    /**
     * Adds a subscription for an agent.
     *
     * @param agentId the agent ID
     * @param topic the topic to subscribe to
     * @return this card for chaining
     */
    public EventDrivenGroupCard addSubscription(String agentId, String topic) {
        if (agentId != null && topic != null) {
            subscriptions.computeIfAbsent(agentId, k -> new ArrayList<>()).add(topic);
        }
        return this;
    }
    
    /**
     * Adds multiple subscriptions for an agent.
     *
     * @param agentId the agent ID
     * @param topics the topics to subscribe to
     * @return this card for chaining
     */
    public EventDrivenGroupCard addSubscriptions(String agentId, List<String> topics) {
        if (agentId != null && topics != null) {
            subscriptions.computeIfAbsent(agentId, k -> new ArrayList<>()).addAll(topics);
        }
        return this;
    }
    
    /**
     * Removes all subscriptions for an agent.
     *
     * @param agentId the agent ID
     * @return this card for chaining
     */
    public EventDrivenGroupCard removeSubscriptions(String agentId) {
        if (agentId != null) {
            subscriptions.remove(agentId);
        }
        return this;
    }
    
    /**
     * Gets the subscribed topics for an agent.
     *
     * @param agentId the agent ID
     * @return the list of subscribed topics, or empty list if none
     */
    public List<String> getSubscribedTopics(String agentId) {
        return subscriptions.getOrDefault(agentId, new ArrayList<>());
    }
    
    /**
     * Gets all agents subscribed to a specific topic.
     *
     * @param topic the topic
     * @return list of agent IDs subscribed to the topic
     */
    public List<String> getSubscribers(String topic) {
        List<String> subscribers = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : subscriptions.entrySet()) {
            if (entry.getValue().contains(topic)) {
                subscribers.add(entry.getKey());
            }
        }
        return subscribers;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Object toolInfo() {
        Map<String, Object> info = (Map<String, Object>) super.toolInfo();
        info.put("type", "event_driven_group");
        info.put("subscriptions", subscriptions);
        return info;
    }
}


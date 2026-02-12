// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Group Identity Card.
 * 
 * <p>Immutable identity information for an agent group.
 * Inherits from BaseCard: id, name, description.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/multi_agent/schema/group_card.py}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class GroupCard extends BaseCard {
    
    /**
     * Agent cards for group members (metadata only, not instances).
     */
    private List<AgentCard> agentCards;
    
    /**
     * Group's primary topic or domain.
     */
    private String topic;
    
    /**
     * Group version string.
     */
    private String version;
    
    /**
     * Optional tags for categorization.
     */
    private List<String> tags;
    
    /**
     * Creates an empty group card.
     */
    public GroupCard() {
        super();
        this.agentCards = new ArrayList<>();
        this.topic = "";
        this.version = "1.0.0";
        this.tags = new ArrayList<>();
    }
    
    /**
     * Creates a group card with the specified name.
     *
     * @param name the group name
     */
    public GroupCard(String name) {
        super(name, "");
        this.agentCards = new ArrayList<>();
        this.topic = "";
        this.version = "1.0.0";
        this.tags = new ArrayList<>();
    }
    
    /**
     * Creates a group card with the specified name and description.
     *
     * @param name the group name
     * @param description the group description
     */
    public GroupCard(String name, String description) {
        super(name, description);
        this.agentCards = new ArrayList<>();
        this.topic = "";
        this.version = "1.0.0";
        this.tags = new ArrayList<>();
    }
    
    /**
     * Creates a group card with full details.
     *
     * @param name the group name
     * @param description the group description
     * @param topic the group's primary topic
     * @param version the group version
     */
    public GroupCard(String name, String description, String topic, String version) {
        super(name, description);
        this.agentCards = new ArrayList<>();
        this.topic = topic != null ? topic : "";
        this.version = version != null ? version : "1.0.0";
        this.tags = new ArrayList<>();
    }
    
    // ========== Getters and Setters ==========
    
    /**
     * Gets the list of agent cards.
     *
     * @return the list of agent cards
     */
    public List<AgentCard> getAgentCards() {
        return agentCards;
    }
    
    /**
     * Sets the list of agent cards.
     *
     * @param agentCards the agent cards
     */
    public void setAgentCards(List<AgentCard> agentCards) {
        this.agentCards = agentCards != null ? agentCards : new ArrayList<>();
    }
    
    /**
     * Gets the topic.
     *
     * @return the topic
     */
    public String getTopic() {
        return topic;
    }
    
    /**
     * Sets the topic.
     *
     * @param topic the topic
     */
    public void setTopic(String topic) {
        this.topic = topic != null ? topic : "";
    }
    
    /**
     * Gets the version.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * Sets the version.
     *
     * @param version the version
     */
    public void setVersion(String version) {
        this.version = version != null ? version : "1.0.0";
    }
    
    /**
     * Gets the tags.
     *
     * @return the tags
     */
    public List<String> getTags() {
        return tags;
    }
    
    /**
     * Sets the tags.
     *
     * @param tags the tags
     */
    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }
    
    // ========== Builder-style methods ==========
    
    /**
     * Adds an agent card.
     *
     * @param agentCard the agent card to add
     * @return this card for chaining
     */
    public GroupCard addAgentCard(AgentCard agentCard) {
        if (agentCard != null) {
            this.agentCards.add(agentCard);
        }
        return this;
    }
    
    /**
     * Adds a tag.
     *
     * @param tag the tag to add
     * @return this card for chaining
     */
    public GroupCard addTag(String tag) {
        if (tag != null && !tag.isEmpty()) {
            this.tags.add(tag);
        }
        return this;
    }
    
    /**
     * Sets the topic with chaining.
     *
     * @param topic the topic
     * @return this card for chaining
     */
    public GroupCard withTopic(String topic) {
        setTopic(topic);
        return this;
    }
    
    /**
     * Sets the version with chaining.
     *
     * @param version the version
     * @return this card for chaining
     */
    public GroupCard withVersion(String version) {
        setVersion(version);
        return this;
    }
    
    @Override
    public Object toolInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("id", getId());
        info.put("name", getName());
        info.put("description", getDescription());
        info.put("type", "group");
        info.put("topic", topic);
        info.put("version", version);
        info.put("tags", tags);
        info.put("agentCount", agentCards.size());
        
        // Include agent names
        List<String> agentNames = new ArrayList<>();
        for (AgentCard card : agentCards) {
            agentNames.add(card.getName());
        }
        info.put("agents", agentNames);
        
        return info;
    }
}


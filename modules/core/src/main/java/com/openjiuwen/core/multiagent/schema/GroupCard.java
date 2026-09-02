/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Group Identity Card.
 * Mirrors Python's {@code GroupCard} in {@code multi_agent/schema/group_card.py}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GroupCard extends BaseCard {
    private List<AgentCard> agentCards = new ArrayList<>();

    private String topic = "";

    private String version = "1.0.0";

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> tags = new ArrayList<>();

    /**
     * getAgentCards.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<AgentCard> getAgentCards() {
        return agentCards;
    }

    /**
     * setAgentCards.
     * 
     * @param agentCards agentCards
     * @since 0.1.7
     */
    public void setAgentCards(List<AgentCard> agentCards) {
        this.agentCards = agentCards;
    }

    /**
     * getTopic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTopic() {
        return topic;
    }

    /**
     * setTopic.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * getVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getVersion() {
        return version;
    }

    /**
     * setVersion.
     * 
     * @param version version
     * @since 0.1.7
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * getTags.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * setTags.
     * 
     * @param tags tags
     * @since 0.1.7
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends BaseCard.Builder {
        private List<AgentCard> agentCards = new ArrayList<>();
        private String topic = "";
        private String version = "1.0.0";

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private List<String> tags = new ArrayList<>();

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * agentCards.
         * 
         * @param agentCards agentCards
         * @return the result
         * @since 0.1.7
         */
        public Builder agentCards(List<AgentCard> agentCards) {
            this.agentCards = agentCards;
            return this;
        }

        /**
         * topic.
         * 
         * @param topic topic
         * @return the result
         * @since 0.1.7
         */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * version.
         * 
         * @param version version
         * @return the result
         * @since 0.1.7
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * tags.
         * 
         * @param tags tags
         * @return the result
         * @since 0.1.7
         */
        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public GroupCard build() {
            GroupCard card = new GroupCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setAgentCards(agentCards);
            card.setTopic(topic);
            card.setVersion(version);
            card.setTags(tags);
            return card;
        }
    }
}

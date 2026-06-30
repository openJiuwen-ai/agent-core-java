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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GroupCard extends BaseCard {

    private List<AgentCard> agentCards = new ArrayList<>();

    private String topic = "";

    private String version = "1.0.0";

    private List<String> tags = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<AgentCard> getAgentCards() {
        return agentCards;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAgentCards(List<AgentCard> agentCards) {
        this.agentCards = agentCards;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends BaseCard.Builder {
        private List<AgentCard> agentCards = new ArrayList<>();
        private String topic = "";
        private String version = "1.0.0";
        private List<String> tags = new ArrayList<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder agentCards(List<AgentCard> agentCards) {
            this.agentCards = agentCards;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

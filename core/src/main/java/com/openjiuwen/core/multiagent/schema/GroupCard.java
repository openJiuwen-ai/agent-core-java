/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Group identity card (legacy Group framework).
 *
 * <p>Extends {@link TeamCard} so Group APIs share the same card shape as Team.</p>
 */
public class GroupCard extends TeamCard {

    public GroupCard() {
        super();
    }

    public GroupCard(String id, String name, String description) {
        super(id, name, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String description = "";
        private String topic = "";
        private String version = "1.0.0";
        private List<String> tags = new ArrayList<>();
        private List<AgentCard> agentCards = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
            return this;
        }

        public Builder agentCards(List<AgentCard> agentCards) {
            this.agentCards = agentCards == null ? new ArrayList<>() : new ArrayList<>(agentCards);
            return this;
        }

        public GroupCard build() {
            if (id == null || id.isBlank() || name == null || name.isBlank()) {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                        "error_msg",
                        "GroupCard id and name are required");
            }
            GroupCard card = new GroupCard(id, name, description == null ? "" : description);
            card.setTopic(topic);
            card.setVersion(version);
            card.setTags(tags);
            card.setAgentCards(agentCards);
            return card;
        }
    }
}

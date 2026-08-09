/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Team identity card.
 *
 * <p>Mirrors Python's {@code TeamCard} in
 * {@code openjiuwen/core/multi_agent/schema/team_card.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamCard extends BaseCard {

    @JsonProperty("agent_cards")
    private List<AgentCard> agentCards = new ArrayList<>();

    @JsonProperty("topic")
    private String topic = "";

    @JsonProperty("version")
    private String version = "1.0.0";

    @JsonProperty("tags")
    private List<String> tags = new ArrayList<>();

    public TeamCard() {
        super();
    }

    public TeamCard(String id, String name, String description) {
        super(id, name, description);
    }

    public List<AgentCard> getAgentCards() {
        return List.copyOf(agentCards);
    }

    public void setAgentCards(List<AgentCard> agentCards) {
        this.agentCards = agentCards == null ? new ArrayList<>() : new ArrayList<>(agentCards);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic == null ? "" : topic;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "1.0.0" : version;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }
}

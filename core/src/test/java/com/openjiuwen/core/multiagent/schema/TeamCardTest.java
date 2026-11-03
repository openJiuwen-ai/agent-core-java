/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;

/**
 * Focused smoke for team card DTOs.
 */
public final class TeamCardTest {

    private TeamCardTest() {
    }

    public static void main(String[] args) {
        TeamCard card = new TeamCard("team-1", "team", "desc");
        require(card.getAgentCards().isEmpty(), "default agent cards");
        require("".equals(card.getTopic()), "default topic");
        require("1.0.0".equals(card.getVersion()), "default version");
        require(card.getTags().isEmpty(), "default tags");

        AgentCard agentCard = new AgentCard("agent-1", "agent", "agent desc");
        card.setAgentCards(List.of(agentCard));
        card.setTopic("support");
        card.setVersion("2.0.0");
        card.setTags(List.of("a", "b"));
        require(card.getAgentCards().size() == 1, "agent cards");
        require("support".equals(card.getTopic()), "topic");
        require("2.0.0".equals(card.getVersion()), "version");
        require(card.getTags().equals(List.of("a", "b")), "tags");

        EventDrivenTeamCard eventCard = new EventDrivenTeamCard("event-team", "event", "event desc");
        eventCard.setSubscriptions(Map.of("agent-1", List.of("topic-1", "topic-2")));
        require(eventCard.getSubscriptions().get("agent-1").equals(List.of("topic-1", "topic-2")),
                "subscriptions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

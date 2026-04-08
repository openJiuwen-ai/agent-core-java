/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.multiagent.legacy.schema.EventDrivenGroupCard;
import com.openjiuwen.core.multiagent.legacy.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LegacyCompatibilityAliasTest {

    @Test
    void legacyAgentGroupSessionKeepsSessionHelpers() {
        AgentGroupSession session = new AgentGroupSession("legacy-session", Map.of("mode", "legacy"));

        session.updateState(Map.of("round", 1));

        assertEquals("legacy-session", session.getSessionId());
        assertEquals("legacy", session.getEnv("mode", ""));
        assertEquals(1, session.getState("round"));
        assertInstanceOf(AgentGroupSessionApi.class, session);
    }

    @Test
    void legacyAliasTypesMatchPythonImportNames() {
        GroupCard card = new GroupCard();
        card.setName("legacy-group");
        card.setDescription("legacy group");
        card.setTopic("routing");

        EventDrivenGroupCard eventDrivenCard = new EventDrivenGroupCard();
        eventDrivenCard.setName("legacy-event-group");
        eventDrivenCard.setDescription("legacy event group");
        eventDrivenCard.setTopic("events");
        eventDrivenCard.setSubscriptions(Map.of("worker", List.of("task")));

        BaseGroup group = new BaseGroup(new AgentGroupConfig("legacy-group")) {
            @Override
            public Object invoke(Object message, AgentGroupSessionApi session) {
                return Map.of("message", message, "session", session != null ? session.getSessionId() : null);
            }

            @Override
            public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
                return List.<Object>of(message).iterator();
            }
        };

        assertEquals("routing", card.getTopic());
        assertEquals(List.of("task"), eventDrivenCard.getSubscriptions().get("worker"));
        assertEquals("legacy-group", group.getGroupId());
    }
}

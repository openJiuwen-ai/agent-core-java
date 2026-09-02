/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.multiagent.GroupConfig;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * System tests for the multiagent module.
 */
@Tag("system-test")
class MultiAgentSystemTest extends SystemTestSupport {
    @Test
    @DisplayName("BaseGroup aggregates child agent outputs with group session envs")
    void testBaseGroupInvokeAggregatesChildAgentOutputs() {
        AggregatingGroup group = new AggregatingGroup(
                GroupCard.builder().id(uniqueId("group")).name(uniqueId("group-name"))
                        .description("multiagent system test group").build(),
                new GroupConfig().configureMaxAgents(2).configureConcurrency(2).configureTimeout(10.0));
        group.addAgent(new TemplateAgent(uniqueId("alpha"), "alpha"), "alpha");
        group.addAgent(new TemplateAgent(uniqueId("beta"), "beta"), "beta");

        AgentGroupSessionApi session =
            AgentGroupSessionApi.create(trackSessionId("group-session"), Map.of("topic", "incident-response"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) group.invoke(Map.of("query", "hello group"), session);

        assertEquals(2, group.getAgentCount());
        assertEquals(2, group.getCard().getAgentCards().size());
        assertTrue(group.listAgents().containsAll(List.of("alpha", "beta")));
        assertTrue(containsIgnoreCase(flattenText(result), "incident-response"));
        assertTrue(containsIgnoreCase(flattenText(result), "alpha:hello group"));
        assertTrue(containsIgnoreCase(flattenText(result), "beta:hello group"));
    }

    @Test
    @DisplayName("BaseGroup stream emits one chunk per child agent")
    void testBaseGroupStreamEmitsChildChunks() {
        AggregatingGroup group =
            new AggregatingGroup(
                    GroupCard.builder().id(uniqueId("stream-group")).name(uniqueId("stream-group-name"))
                            .description("multiagent stream test group").build(),
                    new GroupConfig().configureMaxAgents(2));
        group.addAgent(new TemplateAgent(uniqueId("stream-alpha"), "stream-alpha"), "alpha");
        group.addAgent(new TemplateAgent(uniqueId("stream-beta"), "stream-beta"), "beta");

        AgentGroupSessionApi session =
            AgentGroupSessionApi.create(trackSessionId("group-stream-session"), Map.of("topic", "streaming"));

        List<Object> streamItems = collect(group.stream(Map.of("query", "fanout"), session));

        assertEquals(2, streamItems.size());
        assertTrue(containsIgnoreCase(flattenText(streamItems), "stream-alpha:fanout"));
        assertTrue(containsIgnoreCase(flattenText(streamItems), "stream-beta:fanout"));
    }

    private static final class AggregatingGroup extends BaseGroup {
        private AggregatingGroup(GroupCard card, GroupConfig config) {
            super(card, config);
        }

        @Override
        public Object invoke(Object message, AgentGroupSessionApi session) {
            Map<String, Object> inputs = normalizeInputs(message);
            String topic = String.valueOf(session.getEnv("topic", ""));
            Map<String, Object> results = new LinkedHashMap<>();
            results.put("group_id", getGroupId());
            results.put("topic", topic);

            for (Map.Entry<String, BaseAgent> entry : getAgents().entrySet()) {
                AgentSessionApi agentSession = AgentSessionApi.create(session.getSessionId() + "-" + entry.getKey(),
                        Map.of("topic", topic), entry.getValue().getCard());
                results.put(entry.getKey(), entry.getValue().invoke(inputs, agentSession));
            }
            return results;
        }

        @Override
        public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
            Map<String, Object> inputs = normalizeInputs(message);
            String topic = String.valueOf(session.getEnv("topic", ""));
            List<Object> items = new ArrayList<>();

            for (Map.Entry<String, BaseAgent> entry : getAgents().entrySet()) {
                AgentSessionApi agentSession = AgentSessionApi.create(session.getSessionId() + "-" + entry.getKey(),
                        Map.of("topic", topic), entry.getValue().getCard());
                items.add(Map.of("agent_id", entry.getKey(), "payload", entry.getValue().invoke(inputs, agentSession)));
            }
            return items.iterator();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> normalizeInputs(Object message) {
            if (message instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("query", String.valueOf(message));
        }
    }

    private static final class TemplateAgent extends BaseAgent {
        private final String prefix;

        private TemplateAgent(String agentId, String prefix) {
            super(AgentCard.builder().id(agentId).name(agentId).description("template agent").build());
            this.prefix = prefix;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return prefix == null ? Map.of() : Map.of("prefix", prefix);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String query = extractQuery(inputs);
            Object topic =
                session instanceof AgentSessionApi agentSessionApi ? agentSessionApi.getEnv("topic", "") : "";
            return Map.of("agent_id", getCard().getId(), "message", prefix + ":" + query, "topic",
                    String.valueOf(topic));
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.<Object>of(invoke(inputs, session)).iterator();
        }

        @SuppressWarnings("unchecked")
        private String extractQuery(Object inputs) {
            if (inputs instanceof Map<?, ?> map) {
                Object query = ((Map<String, Object>) map).get("query");
                return query == null ? "" : String.valueOf(query);
            }
            return String.valueOf(inputs);
        }
    }
}

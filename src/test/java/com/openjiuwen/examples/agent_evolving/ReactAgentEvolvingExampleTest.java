/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReactAgentEvolvingExampleTest {

    @Test
    void createReactAgentConfiguresPromptModelAndIterations() {
        ReActAgentEvolve agent = ReactAgentEvolvingExample.createReactAgent("system prompt", "react_agent_evolving");

        assertEquals("react_agent_evolving", agent.getCard().getId());
        assertInstanceOf(ReActAgentConfig.class, agent.getConfig());

        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertEquals(System.getenv().getOrDefault("MODEL_PROVIDER", "your model provider"), config.getModelProvider());
        assertEquals(System.getenv().getOrDefault("API_BASE", "your api base"), config.getApiBase());
        assertEquals(System.getenv().getOrDefault("API_KEY", "your api key"), config.getApiKey());
        assertEquals(System.getenv().getOrDefault("MODEL_NAME", "your model name"), config.getModelName());
        assertEquals(3, config.getMaxIterations());
        assertEquals(
                List.of(
                        Map.of("role", "system", "content", "system prompt"),
                        Map.of("role", "user", "content", "{{query}}")
                ),
                config.getPromptTemplate()
        );
    }

    @Test
    void testAgentInvokesAgentForEachQuery() {
        RecordingAgent agent = new RecordingAgent();
        List<Map<String, Object>> queries = List.of(
                Map.of("query", "first"),
                Map.of("query", "second", "conversation_id", "case-2")
        );

        ReactAgentEvolvingExample.testAgent(agent, queries);

        assertEquals(queries, agent.seenInputs);
        assertEquals(2, agent.sessionCount);
    }

    private static final class RecordingAgent extends ReActAgentEvolve {
        private final List<Map<String, Object>> seenInputs = new ArrayList<>();
        private int sessionCount;

        private RecordingAgent() {
            super(AgentCard.builder()
                    .id("recording-agent")
                    .name("Recording Agent")
                    .description("Test agent")
                    .build());
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            seenInputs.add(Map.copyOf((Map<String, Object>) inputs));
            if (session != null) {
                sessionCount++;
            }
            return Map.of("output", "answer-" + seenInputs.size());
        }
    }
}

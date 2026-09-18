/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentStream} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_stream.py}.
 */
class WorkflowAgentStreamMissingTest {

    @Test
    void testStreamDirect() {
        DeterministicStreamController controller = new DeterministicStreamController();
        WorkflowAgent agent = agentWith(controller);
        String conversationId = UUID.randomUUID().toString();

        List<Object> chunks = drain(agent.stream(query("hello", conversationId), new RecordingSession(conversationId)));

        assertWorkflowFinal(chunks, "hello");
        assertThat(controller.roles(conversationId)).containsExactly("user", "assistant");
    }

    @Test
    void testStreamViaRunnerManagedSession() {
        DeterministicStreamController controller = new DeterministicStreamController();
        WorkflowAgent agent = agentWith(controller);
        String conversationId = UUID.randomUUID().toString();

        List<Object> chunks = drain(agent.stream(query("hello", conversationId), null, List.of(StreamMode.OUTPUT)));

        assertWorkflowFinal(chunks, "hello");
        assertThat(controller.roles(conversationId)).containsExactly("user", "assistant");
    }

    private static void assertWorkflowFinal(List<Object> chunks, String expectedResult) {
        assertThat(chunks).isNotEmpty();
        List<OutputSchema> finalChunks = chunks.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(chunk -> "workflow_final".equals(chunk.getType()))
                .toList();
        assertThat(finalChunks).hasSize(1);
        assertThat(map(finalChunks.get(0).getPayload())).containsEntry("result", expectedResult);
    }

    private static WorkflowAgent agentWith(DeterministicStreamController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId("workflow-agent-stream-missing-test");
        config.setVersion("1.0");
        config.setDescription("workflow agent stream missing-test parity");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(controller);
        return agent;
    }

    private static Map<String, Object> query(Object query, String conversationId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", conversationId);
        return inputs;
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    public static final class DeterministicStreamController {
        private final ConcurrentMap<String, List<String>> chatRoles = new ConcurrentHashMap<>();

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            String sessionId = session.getSessionId();
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
            return List.of((Object) new OutputSchema(
                    "workflow_final",
                    0,
                    Map.of("result", String.valueOf(inputs.get("query")))
            )).iterator();
        }

        List<String> roles(String sessionId) {
            return List.copyOf(chatRoles.getOrDefault(sessionId, List.of()));
        }

        private void appendRole(String sessionId, String role) {
            chatRoles.compute(sessionId, (ignored, roles) -> {
                List<String> updated = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
                updated.add(role);
                return updated;
            });
        }
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            if (data != null) {
                state.putAll(data);
            }
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}

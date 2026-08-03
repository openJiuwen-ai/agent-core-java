/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentInvoke} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_invoke.py}.
 */
class WorkflowAgentInvokeMissingTest {

    @Test
    void testInvokeDirect() {
        DeterministicInvokeController controller = new DeterministicInvokeController();
        WorkflowAgent agent = agentWith(controller);
        String conversationId = UUID.randomUUID().toString();

        Map<String, Object> result = map(agent.invoke(query("hello", conversationId), new RecordingSession(conversationId))
                .toCompletableFuture()
                .join());

        assertThat(result).containsEntry("result_type", "answer");
        WorkflowOutput output = output(result.get("output"));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(output.getResult())).containsEntry("result", "hello");
        assertThat(controller.roles(conversationId)).containsExactly("user", "assistant");
    }

    @Test
    void testInvokeViaRunner() {
        DeterministicInvokeController controller = new DeterministicInvokeController();
        WorkflowAgent agent = agentWith(controller);
        String conversationId = UUID.randomUUID().toString();

        Map<String, Object> result = map(agent.invoke(query("hello", conversationId), null)
                .toCompletableFuture()
                .join());

        assertThat(result).containsEntry("result_type", "answer");
        WorkflowOutput output = output(result.get("output"));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(output.getResult())).containsEntry("result", "hello");
        assertThat(controller.roles(conversationId)).containsExactly("user", "assistant");
    }

    private static WorkflowAgent agentWith(DeterministicInvokeController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId("workflow-agent-invoke-missing-test");
        config.setVersion("1.0");
        config.setDescription("workflow agent invoke missing-test parity");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static WorkflowOutput output(Object value) {
        assertThat(value).isInstanceOf(WorkflowOutput.class);
        return (WorkflowOutput) value;
    }

    public static final class DeterministicInvokeController {
        private final ConcurrentMap<String, List<String>> chatRoles = new ConcurrentHashMap<>();

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            String sessionId = session.getSessionId();
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
            Object query = inputs.get("query");
            WorkflowOutput output = new WorkflowOutput(Map.of("result", query), WorkflowExecutionState.COMPLETED);
            return CompletableFuture.completedFuture(Map.of("result_type", "answer", "output", output));
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

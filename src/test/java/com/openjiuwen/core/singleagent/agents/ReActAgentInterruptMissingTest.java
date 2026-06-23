/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's ReActAgent interruption/resume tests in
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_interrupt.py}.
 */
class ReActAgentInterruptMissingTest {

    @Nested
    class TestIsInterrupted {

        @Test
        void detectsWorkflowInputRequired() {
            ReActAgent agent = makeAgent("agent_is_interrupted");
            WorkflowOutput interrupted = new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED);
            WorkflowOutput completed = new WorkflowOutput(null, WorkflowExecutionState.COMPLETED);

            assertThat(agent.isInterrupted(interrupted)).isTrue();
            assertThat(agent.isInterrupted(completed)).isFalse();
        }

        @Test
        void detectsListWithInteractionItem() {
            ReActAgent agent = makeAgent("agent_is_interrupted_list");

            assertThat(agent.isInterrupted(List.of(new InteractionItem("__interaction__", Map.of())))).isTrue();
            assertThat(agent.isInterrupted(List.of(new InteractionItem("normal", Map.of())))).isFalse();
            assertThat(agent.isInterrupted("plain string")).isFalse();
        }
    }

    @Nested
    class TestAfterExecuteToolCall {

        @Test
        void noInterruptReturnsNull() {
            ReActAgent agent = makeAgent("agent_after_exec_none");
            AssistantMessage aiMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(toolCall("c1", "tool_a")))
                    .build();

            InterruptionState state = agent.afterExecuteToolCall(
                    List.of(new AbilityManager.ExecutionResult("plain result", toolMessage("c1"))),
                    aiMessage.getToolCalls(),
                    aiMessage,
                    0,
                    "original");

            assertThat(state).isNull();
        }

        @Test
        void buildsStateForFirstInterruptedTool() {
            ReActAgent agent = makeAgent("agent_after_exec_state");
            AssistantMessage aiMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(toolCall("c1", "tool_a"), toolCall("c2", "tool_b")))
                    .build();
            WorkflowOutput interrupted = new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED);

            InterruptionState state = agent.afterExecuteToolCall(
                    List.of(
                            new AbilityManager.ExecutionResult("ok", toolMessage("c1")),
                            new AbilityManager.ExecutionResult(interrupted, toolMessage("c2"))),
                    aiMessage.getToolCalls(),
                    aiMessage,
                    1,
                    "original");

            assertThat(state).isNotNull();
            assertThat(state.getIteration()).isEqualTo(1);
            assertThat(state.getInterruptedWorkflows()).containsKey("tool_b");
            assertThat(state.getPendingWorkflowId()).isEqualTo("tool_b");
            assertThat(state.getInterruptedWorkflows().get("tool_b").getToolCall().getId()).isEqualTo("c2");
        }
    }

    @Nested
    class TestSessionStateManagement {

        @Test
        void saveLoadClearCycle() {
            ReActAgent agent = makeAgent("agent_session_state");
            MemorySession session = new MemorySession("sess_state_001");
            InterruptionState fakeState = new InterruptionState();
            fakeState.setAiMessage(new AssistantMessage("test"));

            agent.saveInterruptionState(fakeState, session);
            InterruptionState loaded = agent.loadInterruptionState(session);
            assertThat(loaded).isNotNull();
            assertThat(loaded.getAiMessage()).isSameAs(fakeState.getAiMessage());

            agent.clearInterruptionState(session);
            assertThat(agent.loadInterruptionState(session)).isNull();

            agent.saveInterruptionState(fakeState, null);
            assertThat(agent.loadInterruptionState(null)).isNull();
        }
    }

    @Nested
    class TestInvokeInterruptResume {

        @Test
        void invokeInterruptThenResume() {
            WorkflowOutput interrupted = new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED);
            ScriptedReActAgent agent = new ScriptedReActAgent(
                    "agent_invoke_resume",
                    List.of(
                            AssistantMessage.builder()
                                    .content("")
                                    .toolCalls(List.of(toolCall("c1", "my_workflow")))
                                    .build(),
                            new AssistantMessage("Resume complete!")),
                    List.of(
                            List.of(new AbilityManager.ExecutionResult(interrupted, toolMessage("c1"))),
                            List.of(new AbilityManager.ExecutionResult("workflow completed", toolMessage("c1")))));
            MemorySession session = new MemorySession("sess_resume_001");

            Object first = agent.invoke(Map.of("query", "start"), session).toCompletableFuture().join();
            assertThat(first).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) first).get("result_type")).isEqualTo("interrupt");

            Object second = agent.invoke(Map.of("query", "user feedback"), session).toCompletableFuture().join();
            assertThat(second).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) second).get("result_type")).isEqualTo("answer");
            assertThat(((Map<?, ?>) second).get("output")).asString().contains("Resume complete!");
        }

        @Test
        void multiPendingCollectsFeedbackOneByOne() {
            WorkflowOutput interrupted = new WorkflowOutput(null, WorkflowExecutionState.INPUT_REQUIRED);
            ScriptedReActAgent agent = new ScriptedReActAgent(
                    "agent_multi_pending",
                    List.of(
                            AssistantMessage.builder()
                                    .content("")
                                    .toolCalls(List.of(toolCall("c1", "wf_a"), toolCall("c2", "wf_b")))
                                    .build(),
                            new AssistantMessage("Both workflows done!")),
                    List.of(
                            List.of(
                                    new AbilityManager.ExecutionResult(interrupted, toolMessage("c1")),
                                    new AbilityManager.ExecutionResult(interrupted, toolMessage("c2"))),
                            List.of(
                                    new AbilityManager.ExecutionResult("wf_a done", toolMessage("c1")),
                                    new AbilityManager.ExecutionResult("wf_b done", toolMessage("c2")))));
            MemorySession session = new MemorySession("sess_multi_001");

            Object first = agent.invoke(Map.of("query", "start"), session).toCompletableFuture().join();
            assertThat(((Map<?, ?>) first).get("result_type")).isEqualTo("interrupt");

            Object second = agent.invoke(Map.of("query", "feedback for c1"), session).toCompletableFuture().join();
            assertThat(((Map<?, ?>) second).get("result_type")).isEqualTo("interrupt");

            Object third = agent.invoke(Map.of("query", "feedback for c2"), session).toCompletableFuture().join();
            assertThat(((Map<?, ?>) third).get("result_type")).isEqualTo("answer");
            assertThat(((Map<?, ?>) third).get("output")).asString().contains("Both workflows done!");

            assertThat(agent.executeCalls).hasSize(2);
            assertThat(agent.executeCalls.get(1)).contains("c1", "c2");
        }
    }

    private static ReActAgent makeAgent(String agentId) {
        return new ScriptedReActAgent(agentId, List.of(new AssistantMessage("unused")), List.of());
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder().id(id).type("function").name(name).arguments("{}").build();
    }

    private static ToolMessage toolMessage(String toolCallId) {
        return new ToolMessage("tool result", toolCallId);
    }

    public static final class InteractionItem {
        public final String type;
        public final Object payload;

        private InteractionItem(String type, Object payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private static final class ScriptedReActAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Queue<List<AbilityManager.ExecutionResult>> executionResults;
        private final List<List<String>> executeCalls = new ArrayList<>();

        private ScriptedReActAgent(String agentId,
                                   List<AssistantMessage> modelResponses,
                                   List<List<AbilityManager.ExecutionResult>> executionResults) {
            super(new AgentCard(agentId, agentId, "test agent"));
            this.modelResponses = new ArrayDeque<>(modelResponses);
            this.executionResults = new ArrayDeque<>(executionResults);
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")));
            config.setMaxIterations(4);
            configure(config);
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context,
                                List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> tools) {
            return modelResponses.remove();
        }

        @Override
        public List<AbilityManager.ExecutionResult> executeToolCall(AgentCallbackContext ctx,
                                                                    List<ToolCall> toolCalls,
                                                                    AgentSessionApi session,
                                                                    ModelContext context) {
            executeCalls.add(toolCalls.stream().map(ToolCall::getId).toList());
            return executionResults.remove();
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
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
            state.putAll(data);
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

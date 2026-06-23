/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
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
 * Mirrors Python's workflow interrupt/resume mock tests in
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_workflow_interrupt_mock.py}.
 */
class ReActAgentWorkflowInterruptMockPythonParityTest {

    @Test
    void testSingleInterruptThenResume() {
        ScriptedWorkflowAgent agent = agent(
                List.of(toolResponse("call_001", "wf_single"), textResponse("task complete: name collected")),
                List.of(
                        List.of(interrupted("call_001", "questioner")),
                        List.of(completed("call_001", "done"))
                )
        );
        MemorySession session = new MemorySession("TestScenario1SingleWorkflowSingleInterrupt");

        List<OutputSchema> first = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", "collect name"),
                session,
                List.of(StreamMode.OUTPUT)
        ));

        assertThat(interactions(first)).extracting(ReActAgentWorkflowInterruptMockPythonParityTest::interactionId)
                .containsExactly("questioner");

        InteractiveInput answer = new InteractiveInput();
        answer.update("questioner", "Zhang San");
        List<OutputSchema> second = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", answer),
                session,
                List.of(StreamMode.OUTPUT)
        ));

        assertThat(interactions(second)).isEmpty();
        assertThat(lastPayload(second)).contains("task complete");
        assertThat(historyRoles(agent, session)).containsExactly(
                "user", "assistant", "tool", "user", "assistant", "tool", "assistant"
        );
    }

    @Test
    void testParallelInterruptSequentialResume() {
        ScriptedWorkflowAgent agent = agent(
                List.of(toolResponse("call_p_001", "wf_parallel"), textResponse("task complete: name and address")),
                List.of(
                        List.of(interrupted("call_p_001", "questioner_1")),
                        List.of(interrupted("call_p_001", "questioner_2")),
                        List.of(completed("call_p_001", "done"))
                )
        );
        MemorySession session = new MemorySession("TestScenario2SingleWorkflowParallelInterrupt");

        List<OutputSchema> first = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", "collect info"),
                session,
                List.of(StreamMode.OUTPUT)
        ));
        assertThat(interactions(first)).extracting(ReActAgentWorkflowInterruptMockPythonParityTest::interactionId)
                .containsExactly("questioner_1");

        InteractiveInput name = new InteractiveInput();
        name.update("questioner_1", "Li Si");
        List<OutputSchema> second = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", name),
                session,
                List.of(StreamMode.OUTPUT)
        ));
        assertThat(interactions(second)).extracting(ReActAgentWorkflowInterruptMockPythonParityTest::interactionId)
                .containsExactly("questioner_2");

        InteractiveInput address = new InteractiveInput();
        address.update("questioner_2", "Beijing");
        List<OutputSchema> third = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", address),
                session,
                List.of(StreamMode.OUTPUT)
        ));

        assertThat(interactions(third)).isEmpty();
        assertThat(lastPayload(third)).contains("task complete");
    }

    @Test
    void testTwoWorkflowsSequentialInterruptThenConcurrentResume() {
        ScriptedWorkflowAgent agent = agent(
                List.of(
                        AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(
                                        toolCall("call_a_001", "wf_a"),
                                        toolCall("call_b_001", "wf_b")
                                ))
                                .build(),
                        textResponse("task complete: both workflows")
                ),
                List.of(
                        List.of(interrupted("call_a_001", "questioner_a"), interrupted("call_b_001", "questioner_b")),
                        List.of(completed("call_a_001", "name done"), completed("call_b_001", "address done"))
                )
        );
        MemorySession session = new MemorySession("TestScenario3TwoWorkflowsEachInterrupt");

        List<OutputSchema> first = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", "collect both"),
                session,
                List.of(StreamMode.OUTPUT)
        ));
        assertThat(interactions(first)).extracting(ReActAgentWorkflowInterruptMockPythonParityTest::interactionId)
                .containsExactly("questioner_a");

        InteractiveInput name = new InteractiveInput();
        name.update("questioner_a", "Li Si");
        List<OutputSchema> second = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", name),
                session,
                List.of(StreamMode.OUTPUT)
        ));
        assertThat(interactions(second)).extracting(ReActAgentWorkflowInterruptMockPythonParityTest::interactionId)
                .containsExactly("questioner_b");

        InteractiveInput address = new InteractiveInput();
        address.update("questioner_b", "Beijing");
        List<OutputSchema> third = drain(agent.stream(
                Map.of("conversation_id", session.getSessionId(), "query", address),
                session,
                List.of(StreamMode.OUTPUT)
        ));

        assertThat(interactions(third)).isEmpty();
        assertThat(lastPayload(third)).contains("task complete");
        assertThat(agent.executeCalls).hasSize(2);
        assertThat(agent.executeCalls.get(1)).containsExactly("call_a_001", "call_b_001");
    }

    private static ScriptedWorkflowAgent agent(List<AssistantMessage> modelResponses,
                                               List<List<AbilityManager.ExecutionResult>> executionResults) {
        ScriptedWorkflowAgent agent = new ScriptedWorkflowAgent(modelResponses, executionResults);
        agent.configure(new ReActAgentConfig()
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .configureMaxIterations(5));
        return agent;
    }

    private static AssistantMessage textResponse(String content) {
        return new AssistantMessage(content);
    }

    private static AssistantMessage toolResponse(String id, String name) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall(id, name)))
                .build();
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder().id(id).type("function").name(name).arguments("{\"query\":\"mock\"}").build();
    }

    private static AbilityManager.ExecutionResult interrupted(String toolCallId, String componentId) {
        return new AbilityManager.ExecutionResult(
                new WorkflowOutput(List.of(interaction(componentId)), WorkflowExecutionState.INPUT_REQUIRED),
                new ToolMessage("[INTERRUPTED - Waiting for user input]", toolCallId)
        );
    }

    private static AbilityManager.ExecutionResult completed(String toolCallId, String content) {
        return new AbilityManager.ExecutionResult(
                new WorkflowOutput(Map.of("result", content), WorkflowExecutionState.COMPLETED),
                new ToolMessage(content, toolCallId)
        );
    }

    private static OutputSchema interaction(String componentId) {
        return new OutputSchema("__interaction__", 0, new InteractionOutput(componentId, "question"));
    }

    private static List<OutputSchema> drain(Iterator<Object> iterator) {
        List<OutputSchema> schemas = new ArrayList<>();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema schema) {
                schemas.add(schema);
            }
        }
        return schemas;
    }

    private static List<OutputSchema> interactions(List<OutputSchema> values) {
        return values.stream().filter(schema -> "__interaction__".equals(schema.getType())).toList();
    }

    private static String interactionId(OutputSchema schema) {
        return ((InteractionOutput) schema.getPayload()).getId();
    }

    private static String lastPayload(List<OutputSchema> values) {
        assertThat(values).isNotEmpty();
        return String.valueOf(values.getLast().getPayload());
    }

    private static List<String> historyRoles(ScriptedWorkflowAgent agent, MemorySession session) {
        ModelContext context = agent.getContextEngine()
                .getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId());
        return context.getMessages(null, true).stream().map(BaseMessage::getRole).toList();
    }

    private static final class ScriptedWorkflowAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Queue<List<AbilityManager.ExecutionResult>> executionResults;
        private final List<List<String>> executeCalls = new ArrayList<>();

        private ScriptedWorkflowAgent(List<AssistantMessage> modelResponses,
                                      List<List<AbilityManager.ExecutionResult>> executionResults) {
            super(new AgentCard("workflow_interrupt_agent", "workflow_interrupt_agent", "test agent"));
            this.modelResponses = new ArrayDeque<>(modelResponses);
            this.executionResults = new ArrayDeque<>(executionResults);
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            return modelResponses.remove();
        }

        @Override
        public List<AbilityManager.ExecutionResult> executeToolCall(AgentCallbackContext ctx,
                                                                    List<ToolCall> toolCalls,
                                                                    AgentSessionApi session,
                                                                    ModelContext context) {
            executeCalls.add(toolCalls.stream().map(ToolCall::getId).toList());
            List<AbilityManager.ExecutionResult> results = executionResults.remove();
            for (AbilityManager.ExecutionResult result : results) {
                if (result.toolMessage() != null) {
                    context.addMessages(result.toolMessage()).toCompletableFuture().join();
                }
            }
            return results;
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();
        private int streamCursor;

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
            List<Object> unread = new ArrayList<>(stream.subList(streamCursor, stream.size()));
            streamCursor = stream.size();
            return unread.iterator();
        }
    }
}

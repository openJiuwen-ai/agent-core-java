/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
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
 * Mirrors Python's stream-mode HITL tests in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_interrupt_stream.py}.
 */
class InterruptStreamPythonParityTest {

    @Test
    void testHitlRailStreamInterruptDetected() {
        ScriptedStreamAgent agent = scriptedAgent(
                toolResponse("read-1", "read", "{\"filepath\":\"/tmp/test.txt\"}"),
                textResponse("File read")
        );
        MemorySession session = new MemorySession("493");

        List<Object> outputs = collect(agent.stream(Map.of(
                "query", "Call read tool, read /tmp/test.txt",
                "conversation_id", "493"
        ), session, List.of()));

        OutputSchema interaction = firstInteraction(outputs);
        assertThat(interaction).isNotNull();
        assertThat(session.getState(InterruptConstants.INTERRUPTION_KEY))
                .isInstanceOf(ToolInterruptionState.class);
        assertThat(interactionId(interaction)).isEqualTo("read-1");
        assertThat(agent.invokeCount("read")).isZero();
    }

    @Test
    void testHitlRailStreamAgreeWithAutoconfirm() {
        ScriptedStreamAgent agent = scriptedAgent(
                toolResponse("read-1", "read", "{\"filepath\":\"/tmp/test1.txt\"}"),
                textResponse("File 1 read"),
                toolResponse("read-2", "read", "{\"filepath\":\"/tmp/test2.txt\"}"),
                textResponse("File 2 read")
        );
        MemorySession session = new MemorySession("493");

        List<Object> outputs1 = collect(agent.stream(Map.of(
                "query", "Please read /tmp/test1.txt",
                "conversation_id", "493"
        ), session, List.of()));
        String toolCallId = interactionId(firstInteraction(outputs1));
        assertThat(session.getState(InterruptConstants.INTERRUPTION_KEY))
                .isInstanceOf(ToolInterruptionState.class);

        List<Object> outputs2 = collect(agent.stream(Map.of(
                "query", confirmInterrupt(toolCallId, true),
                "conversation_id", "493"
        ), session, List.of()));
        assertThat(firstInteraction(outputs2)).isNull();
        assertThat(agent.invokeCount("read")).isEqualTo(1);

        List<Object> outputs3 = collect(agent.stream(Map.of(
                "query", "Please read /tmp/test2.txt",
                "conversation_id", "493"
        ), session, List.of()));
        assertThat(firstInteraction(outputs3)).isNull();
        assertThat(agent.invokeCount("read")).isEqualTo(2);
    }

    @Test
    void testHitlRailStreamReject() {
        ScriptedStreamAgent agent = scriptedAgent(
                toolResponse("read-1", "read", "{\"filepath\":\"/tmp/test.txt\"}"),
                textResponse("Operation completed")
        );
        MemorySession session = new MemorySession("493");

        List<Object> outputs1 = collect(agent.stream(Map.of(
                "query", "Call read tool, read /tmp/test.txt",
                "conversation_id", "493"
        ), session, List.of()));
        String toolCallId = interactionId(firstInteraction(outputs1));
        assertThat(session.getState(InterruptConstants.INTERRUPTION_KEY))
                .isInstanceOf(ToolInterruptionState.class);

        List<Object> outputs2 = collect(agent.stream(Map.of(
                "query", rejectInterrupt(toolCallId, "Reject this operation"),
                "conversation_id", "493"
        ), session, List.of()));

        assertThat(firstInteraction(outputs2)).isNull();
        assertThat(agent.invokeCount("read")).isZero();
    }

    private static ScriptedStreamAgent scriptedAgent(AssistantMessage... responses) {
        return new ScriptedStreamAgent(List.of(responses));
    }

    private static AssistantMessage textResponse(String content) {
        return new AssistantMessage(content);
    }

    private static AssistantMessage toolResponse(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .type("function")
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static InteractiveInput confirmInterrupt(String toolCallId, boolean autoConfirm) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", autoConfirm
        ));
        return input;
    }

    private static InteractiveInput rejectInterrupt(String toolCallId, String feedback) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", false,
                "feedback", feedback
        ));
        return input;
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static OutputSchema firstInteraction(List<Object> outputs) {
        return outputs.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(output -> InterruptConstants.INTERACTION.equals(output.getType()))
                .findFirst()
                .orElse(null);
    }

    private static String interactionId(OutputSchema output) {
        assertThat(output).isNotNull();
        Object payload = output.getPayload();
        assertThat(payload).isInstanceOf(InteractionOutput.class);
        return ((InteractionOutput) payload).getId();
    }

    private static final class ScriptedStreamAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Map<String, Integer> invokeCounts = new LinkedHashMap<>();

        private ScriptedStreamAgent(List<AssistantMessage> responses) {
            super(new AgentCard("stream_interrupt_agent", "stream_interrupt_agent", "test agent"));
            modelResponses = new ArrayDeque<>(responses);
            configure(new ReActAgentConfig()
                    .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are an assistant.")))
                    .configureMaxIterations(8));
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
            List<AbilityManager.ExecutionResult> results = new ArrayList<>();
            for (ToolCall toolCall : toolCalls) {
                results.add(executeOne(ctx, session, toolCall));
            }
            return results;
        }

        private AbilityManager.ExecutionResult executeOne(AgentCallbackContext ctx, AgentSessionApi session,
                                                          ToolCall toolCall) {
            Object resumeInput = ctx.getExtra().get(InterruptConstants.RESUME_USER_INPUT_KEY);
            if (resumeInput instanceof InteractiveInput interactiveInput) {
                Object decision = interactiveInput.getUserInputs().get(toolCall.getId());
                if (decision instanceof Map<?, ?> map && !Boolean.TRUE.equals(map.get("approved"))) {
                    return new AbilityManager.ExecutionResult(
                            "Tool skipped",
                            new ToolMessage("Tool skipped", toolCall.getId(), toolCall.getName())
                    );
                }
                return executedResult(toolCall);
            }
            if (isAutoConfirmed(session, toolCall.getName())) {
                return executedResult(toolCall);
            }
            InterruptRequest request = new InterruptRequest("Please approve or reject?", confirmSchema(),
                    toolCall.getName());
            return new AbilityManager.ExecutionResult(
                    new ToolInterruptException(request, toolCall),
                    new ToolMessage("[INTERRUPTED - Waiting for user input]", toolCall.getId(), toolCall.getName())
            );
        }

        private AbilityManager.ExecutionResult executedResult(ToolCall toolCall) {
            invokeCounts.merge(toolCall.getName(), 1, Integer::sum);
            String content = "Content of file " + toolCall.getArguments();
            return new AbilityManager.ExecutionResult(
                    Map.of("success", true, "data", Map.of("content", content)),
                    new ToolMessage(content, toolCall.getId(), toolCall.getName())
            );
        }

        private int invokeCount(String name) {
            return invokeCounts.getOrDefault(name, 0);
        }

        private static boolean isAutoConfirmed(AgentSessionApi session, String key) {
            if (session == null) {
                return false;
            }
            Object config = session.getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
            return config instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get(key));
        }

        private static Map<String, Object> confirmSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "approved", Map.of("type", "boolean"),
                            "feedback", Map.of("type", "string", "default", ""),
                            "auto_confirm", Map.of("type", "boolean", "default", false)
                    ),
                    "required", List.of("approved")
            );
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
            List<Object> snapshot = new ArrayList<>(stream);
            stream.clear();
            return snapshot.iterator();
        }
    }
}

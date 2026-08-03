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
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
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
 * Mirrors Python's {@code test_hitl_rail_chain_tools} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_hitl_rail_chain_tools.py}.
 */
class HitlRailChainToolsMissingTest {

    @Test
    void testHitlRailChainTools() {
        ScriptedChainAgent agent = new ScriptedChainAgent(List.of(
                toolResponse("call_read", "read", "{\"filepath\":\"/tmp/test.txt\"}"),
                toolResponse("call_write", "write", "{\"filepath\":\"/tmp/test.txt\",\"content\":\"new content\"}"),
                textResponse("Operation completed")
        ));
        MemorySession session = new MemorySession("492");

        Map<String, Object> first = invoke(agent, session, Map.of(
                "query", "Please read the /tmp/test.txt file, then modify it",
                "conversation_id", "492"
        ));
        assertThat(first).containsEntry("result_type", "interrupt");
        assertThat(interruptIds(first)).hasSize(1);
        String readToolCallId = interruptIds(first).get(0);
        assertThat(toolNameFromFirstState(first)).isEqualTo("read");

        Map<String, Object> second = invoke(agent, session, Map.of(
                "query", confirmInterrupt(readToolCallId),
                "conversation_id", "492"
        ));
        assertThat(second).containsEntry("result_type", "interrupt");
        assertThat(interruptIds(second)).hasSize(1);
        String writeToolCallId = interruptIds(second).get(0);
        assertThat(toolNameFromFirstState(second)).isEqualTo("write");
        assertThat(agent.invokeCount("read")).isEqualTo(1);

        Map<String, Object> current = invoke(agent, session, Map.of(
                "query", rejectInterrupt(writeToolCallId, "Reject write operation"),
                "conversation_id", "492"
        ));
        int iteration = 0;
        while ("interrupt".equals(current.get("result_type")) && iteration < 2) {
            List<String> currentIds = interruptIds(current);
            if (currentIds.isEmpty()) {
                break;
            }
            current = invoke(agent, session, Map.of(
                    "query", rejectInterrupt(currentIds.get(0), "Reject"),
                    "conversation_id", "492"
            ));
            iteration++;
        }

        assertThat(current).containsEntry("result_type", "answer");
        assertThat(String.valueOf(current.get("output"))).contains("Operation completed");
        assertThat(agent.invokeCount("read")).isEqualTo(1);
        assertThat(agent.invokeCount("write")).isZero();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(ScriptedChainAgent agent, MemorySession session,
                                              Map<String, Object> inputs) {
        return (Map<String, Object>) agent.invoke(inputs, session).toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    private static List<String> interruptIds(Map<String, Object> result) {
        return (List<String>) result.get("interrupt_ids");
    }

    private static String toolNameFromFirstState(Map<String, Object> result) {
        Object state = result.get("state");
        assertThat(state).isInstanceOf(List.class);
        List<?> states = (List<?>) state;
        assertThat(states).hasSize(1);
        Object payload = ((OutputSchema) states.get(0)).getPayload();
        Object value = ((InteractionOutput) payload).getValue();
        return ((ToolCallInterruptRequest) value).getToolName();
    }

    private static InteractiveInput confirmInterrupt(String toolCallId) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", false
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

    private static final class ScriptedChainAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Map<String, Integer> invokeCounts = new LinkedHashMap<>();

        private ScriptedChainAgent(List<AssistantMessage> modelResponses) {
            super(new AgentCard("rail_chain_agent", "rail_chain_agent", "test agent"));
            this.modelResponses = new ArrayDeque<>(modelResponses);
            configure(new ReActAgentConfig()
                    .configurePromptTemplate(List.of(Map.of(
                            "role", "system",
                            "content", "You are an assistant. Read first, then write. If user rejects, stop."
                    )))
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
                AbilityManager.ExecutionResult result = executeOne(ctx, toolCall);
                results.add(result);
                if (result.toolMessage() != null) {
                    context.addMessages(result.toolMessage()).toCompletableFuture().join();
                }
            }
            return results;
        }

        private AbilityManager.ExecutionResult executeOne(AgentCallbackContext ctx, ToolCall toolCall) {
            Object resumeInput = ctx.getExtra().get(InterruptConstants.RESUME_USER_INPUT_KEY);
            if (resumeInput instanceof InteractiveInput interactiveInput) {
                Object decision = interactiveInput.getUserInputs().get(toolCall.getId());
                if (decision instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("approved"))) {
                    return executedResult(toolCall);
                }
                return skippedResult(toolCall);
            }
            return interruptedResult(toolCall);
        }

        private AbilityManager.ExecutionResult interruptedResult(ToolCall toolCall) {
            InterruptRequest request = new InterruptRequest("Please approve or reject?", confirmSchema(),
                    toolCall.getName());
            return new AbilityManager.ExecutionResult(
                    new ToolInterruptException(request, toolCall),
                    new ToolMessage("[INTERRUPTED - Waiting for user input]", toolCall.getId(), toolCall.getName())
            );
        }

        private AbilityManager.ExecutionResult executedResult(ToolCall toolCall) {
            invokeCounts.merge(toolCall.getName(), 1, Integer::sum);
            String content = "%s executed with %s".formatted(toolCall.getName(), toolCall.getArguments());
            return new AbilityManager.ExecutionResult(
                    Map.of("success", true, "content", content, "invoke_count", invokeCount(toolCall.getName())),
                    new ToolMessage(content, toolCall.getId(), toolCall.getName())
            );
        }

        private AbilityManager.ExecutionResult skippedResult(ToolCall toolCall) {
            return new AbilityManager.ExecutionResult(
                    "Tool skipped",
                    new ToolMessage("Tool skipped", toolCall.getId(), toolCall.getName())
            );
        }

        private int invokeCount(String name) {
            return invokeCounts.getOrDefault(name, 0);
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
            return stream.iterator();
        }
    }
}

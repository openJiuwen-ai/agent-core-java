/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's HITL auto-confirm tests in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_react_agent_auto_confirm.py}.
 */
class ReActAgentAutoConfirmPythonParityTest {

    @Test
    void testHitlRailAutoConfirm() {
        ScriptedAutoConfirmAgent agent = scriptedAgent(
                toolResponse(toolCall("read-1", "read", "{\"filepath\":\"/tmp/test1.txt\"}")),
                textResponse("File 1 read"),
                toolResponse(toolCall("read-2", "read", "{\"filepath\":\"/tmp/test2.txt\"}")),
                textResponse("File 2 read")
        );
        MemorySession session = new MemorySession("497");

        Map<String, Object> result1 = invokeMap(agent, session, Map.of(
                "query", "Please read /tmp/test1.txt",
                "conversation_id", "497"
        ));
        String toolCallId = interruptIds(result1).get(0);

        Map<String, Object> result2 = invokeMap(agent, session, Map.of(
                "query", confirmInterrupt(toolCallId, true),
                "conversation_id", "497"
        ));
        assertAnswer(result2);
        assertThat(agent.invokeCount("read")).isEqualTo(1);

        Map<String, Object> result3 = invokeMap(agent, session, Map.of(
                "query", "Please read /tmp/test2.txt",
                "conversation_id", "497"
        ));
        assertAnswer(result3);
        assertThat(agent.invokeCount("read")).isEqualTo(2);
    }

    @Test
    void testHitlRailSameToolMultipleCalls() {
        ScriptedAutoConfirmAgent agent = scriptedAgent(
                toolResponse(
                        toolCall("c1", "multi_action", "{\"action\":\"action1\"}"),
                        toolCall("c2", "multi_action", "{\"action\":\"action2\"}"),
                        toolCall("c3", "multi_action", "{\"action\":\"action3\"}")
                ),
                textResponse("All actions completed")
        );
        MemorySession session = new MemorySession("497");

        Map<String, Object> current = invokeMap(agent, session, Map.of(
                "query", "Please execute action1, action2, and action3 simultaneously",
                "conversation_id", "497"
        ));
        assertThat(interruptIds(current)).containsExactly("c1", "c2", "c3");

        int confirmedCount = 0;
        while ("interrupt".equals(current.get("result_type"))) {
            String currentId = interruptIds(current).get(0);
            confirmedCount++;
            current = invokeMap(agent, session, Map.of(
                    "query", confirmInterrupt(currentId, false),
                    "conversation_id", "497"
            ));
        }

        assertAnswer(current);
        assertThat(agent.invokeCount("multi_action")).isEqualTo(confirmedCount);
        assertThat(confirmedCount).isEqualTo(3);
    }

    @Test
    void testHitlRailConfirmOneAutoPassOthers() {
        ScriptedAutoConfirmAgent agent = scriptedAgent(
                toolResponse(
                        toolCall("c1", "read", "{\"filepath\":\"/tmp/file1.txt\"}"),
                        toolCall("c2", "read", "{\"filepath\":\"/tmp/file2.txt\"}"),
                        toolCall("c3", "read", "{\"filepath\":\"/tmp/file3.txt\"}")
                ),
                textResponse("All files read")
        );
        MemorySession session = new MemorySession("497");

        Map<String, Object> result1 = invokeMap(agent, session, Map.of(
                "query", "Please read /tmp/file1.txt, /tmp/file2.txt, and /tmp/file3.txt simultaneously",
                "conversation_id", "497"
        ));
        assertThat(interruptIds(result1)).containsExactly("c1", "c2", "c3");

        Map<String, Object> result2 = invokeMap(agent, session, Map.of(
                "query", confirmInterrupt("c1", true),
                "conversation_id", "497"
        ));

        assertAnswer(result2);
        assertThat(agent.invokeCount("read")).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(ScriptedAutoConfirmAgent agent, MemorySession session,
                                                 Map<String, Object> inputs) {
        return (Map<String, Object>) agent.invoke(inputs, session).toCompletableFuture().join();
    }

    private static void assertAnswer(Map<String, Object> result) {
        assertThat(result).containsEntry("result_type", "answer");
    }

    @SuppressWarnings("unchecked")
    private static List<String> interruptIds(Map<String, Object> result) {
        assertThat(result).containsEntry("result_type", "interrupt");
        return (List<String>) result.get("interrupt_ids");
    }

    private static ScriptedAutoConfirmAgent scriptedAgent(AssistantMessage... responses) {
        return new ScriptedAutoConfirmAgent(List.of(responses));
    }

    private static AssistantMessage textResponse(String content) {
        return new AssistantMessage(content);
    }

    private static AssistantMessage toolResponse(ToolCall... calls) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(calls))
                .build();
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
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

    private static final class ScriptedAutoConfirmAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Map<String, Integer> invokeCounts = new LinkedHashMap<>();

        private ScriptedAutoConfirmAgent(List<AssistantMessage> responses) {
            super(new AgentCard("auto_confirm_agent", "auto_confirm_agent", "test agent"));
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
                if (decision instanceof Map<?, ?> map) {
                    if (Boolean.TRUE.equals(map.get("approved"))) {
                        return executedResult(toolCall);
                    }
                    return skippedResult(toolCall);
                }
            }
            if (isAutoConfirmed(session, toolCall.getName())) {
                return executedResult(toolCall);
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
            String content = "Executed " + toolCall.getName() + " with " + toolCall.getArguments();
            return new AbilityManager.ExecutionResult(
                    Map.of("success", true, "data", Map.of("content", content)),
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
            return stream.iterator();
        }
    }
}

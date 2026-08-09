/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
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
 * Mirrors Python's {@code test_3layer_agent_interrupt} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_3layer_agent_interrupt.py}.
 */
class ThreeLayerAgentInterruptMissingTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void test3LayerAgentInterrupt() {
        ReadTool readTool = new ReadTool();
        ScriptedNestedAgent subAgent2 = new ScriptedNestedAgent(
                "sub_agent_2",
                List.of(
                        toolResponse("call_read", "read", "{\"filepath\":\"/tmp/test.txt\"}"),
                        textResponse("File read complete")
                ),
                readTool
        );
        ScriptedNestedAgent subAgent1 = new ScriptedNestedAgent(
                "sub_agent_1",
                List.of(
                        toolResponse("call_sub_agent_2", "sub_agent_2", "{\"query\":\"read file\"}"),
                        textResponse("Sub-agent 1 complete")
                ),
                readTool
        );
        ScriptedNestedAgent mainAgent = new ScriptedNestedAgent(
                "main_agent",
                List.of(
                        toolResponse("call_sub_agent_1", "sub_agent_1", "{\"query\":\"read file\"}"),
                        textResponse("File read complete")
                ),
                readTool
        );
        subAgent1.addSubAgent("sub_agent_2", subAgent2);
        mainAgent.addSubAgent("sub_agent_1", subAgent1);
        MemorySession mainSession = new MemorySession("494");

        Map<String, Object> first = invoke(mainAgent, mainSession, Map.of(
                "query", "Please read file /tmp/test.txt",
                "conversation_id", "494"
        ));

        assertThat(first).containsEntry("result_type", "interrupt");
        assertThat(interruptIds(first)).containsExactly("call_read");
        assertThat(toolNameFromFirstState(first)).isEqualTo("read");

        Map<String, Object> second = invoke(mainAgent, mainSession, Map.of(
                "query", confirmInterrupt("call_read"),
                "conversation_id", "494"
        ));

        assertThat(second).containsEntry("result_type", "answer");
        assertThat(readTool.invokeCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(ScriptedNestedAgent agent, MemorySession session,
                                              Map<String, Object> inputs) {
        return (Map<String, Object>) agent.invoke(inputs, session).toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    private static List<String> interruptIds(Map<String, Object> result) {
        return (List<String>) result.get("interrupt_ids");
    }

    @SuppressWarnings("unchecked")
    private static String toolNameFromFirstState(Map<String, Object> result) {
        List<Object> states = (List<Object>) result.get("state");
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

    private static final class ScriptedNestedAgent extends ReActAgent {
        private final Queue<AssistantMessage> modelResponses;
        private final Map<String, ScriptedNestedAgent> subAgents = new LinkedHashMap<>();
        private final Map<String, MemorySession> childSessions = new LinkedHashMap<>();
        private final ReadTool readTool;

        private ScriptedNestedAgent(String agentId, List<AssistantMessage> modelResponses, ReadTool readTool) {
            super(new AgentCard(agentId, agentId, "nested agent"));
            this.modelResponses = new ArrayDeque<>(modelResponses);
            this.readTool = readTool;
            configure(new ReActAgentConfig()
                    .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are an assistant.")))
                    .configureMaxIterations(4));
        }

        private void addSubAgent(String toolName, ScriptedNestedAgent subAgent) {
            subAgents.put(toolName, subAgent);
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
            ScriptedNestedAgent subAgent = subAgents.get(toolCall.getName());
            if (subAgent != null) {
                MemorySession childSession = childSessions.computeIfAbsent(
                        toolCall.getName(), name -> new MemorySession(name + "_session"));
                Object query = childQuery(ctx, toolCall);
                Map<String, Object> result = ThreeLayerAgentInterruptMissingTest.invoke(subAgent, childSession, Map.of(
                        "query", query,
                        "conversation_id", childSession.getSessionId()
                ));
                return new AbilityManager.ExecutionResult(
                        result,
                        new ToolMessage(String.valueOf(result), toolCall.getId(), toolCall.getName())
                );
            }
            if ("read".equals(toolCall.getName())) {
                return readTool.execute(ctx, toolCall);
            }
            return new AbilityManager.ExecutionResult(
                    "Unknown tool",
                    new ToolMessage("Unknown tool", toolCall.getId(), toolCall.getName())
            );
        }

        private static Object childQuery(AgentCallbackContext ctx, ToolCall toolCall) {
            Object resumeInput = ctx.getExtra().get(InterruptConstants.RESUME_USER_INPUT_KEY);
            if (resumeInput != null) {
                return resumeInput;
            }
            try {
                Map<?, ?> parsed = JSON.readValue(toolCall.getArguments(), Map.class);
                Object query = parsed.get("query");
                return query == null ? "" : query;
            } catch (JsonProcessingException exception) {
                return "";
            }
        }
    }

    private static final class ReadTool {
        private int invokeCount;

        private AbilityManager.ExecutionResult execute(AgentCallbackContext ctx, ToolCall toolCall) {
            Object resumeInput = ctx.getExtra().get(InterruptConstants.RESUME_USER_INPUT_KEY);
            if (resumeInput instanceof InteractiveInput input) {
                Object decision = input.getUserInputs().get(toolCall.getId());
                if (decision instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("approved"))) {
                    invokeCount++;
                    String content = "Content of file /tmp/test.txt";
                    return new AbilityManager.ExecutionResult(
                            Map.of("success", true, "content", content, "invoke_count", invokeCount),
                            new ToolMessage(content, toolCall.getId(), toolCall.getName())
                    );
                }
                return new AbilityManager.ExecutionResult(
                        "Tool skipped",
                        new ToolMessage("Tool skipped", toolCall.getId(), toolCall.getName())
                );
            }
            InterruptRequest request = new InterruptRequest("Please approve or reject?", confirmSchema(), "read");
            return new AbilityManager.ExecutionResult(
                    new ToolInterruptException(request, toolCall),
                    new ToolMessage("[INTERRUPTED - Waiting for user input]", toolCall.getId(), toolCall.getName())
            );
        }

        private int invokeCount() {
            return invokeCount;
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

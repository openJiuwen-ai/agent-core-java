/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgent;
import com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's legacy ReAct Agent mock tests in
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_mock.py}.
 */
class LegacyReActAgentMockPythonParityTest {

    @Test
    void testReactAgentInvokeWithMockLlm() {
        String addToolId = unique("add");
        CountingTool addTool = new CountingTool(addToolId, "add", (a, b) -> a + b);
        LegacyReActAgent agent = agent(
                "react_agent_mock_test",
                addTool,
                toolResponse("add", "{\"a\":1,\"b\":2}"),
                textResponse("1 + 2 = 3")
        );

        try {
            Map<String, Object> result = invokeMap(agent, "test_session", "calculate 1+2");

            assertThat(result).containsEntry("result_type", "answer");
            assertThat(String.valueOf(result.get("output"))).contains("3");
            assertThat(addTool.invokeCount()).isEqualTo(1);
            assertThat(((ScriptedModelClient) agent.getLlm()).callCount()).isEqualTo(2);
        } finally {
            Runner.resourceMgr.removeTool(addToolId);
        }
    }

    @Test
    void testReactAgentMultiTurnToolCalls() {
        String addToolId = unique("add");
        String multiplyToolId = unique("multiply");
        CountingTool addTool = new CountingTool(addToolId, "add", (a, b) -> a + b);
        CountingTool multiplyTool = new CountingTool(multiplyToolId, "multiply", (a, b) -> a * b);
        LegacyReActAgent agent = agent(
                "react_agent_multi_turn",
                List.of(addTool, multiplyTool),
                toolResponse("add", "{\"a\":1,\"b\":2}"),
                toolResponse("multiply", "{\"a\":3,\"b\":3}"),
                textResponse("(1 + 2) * 3 = 9")
        );

        try {
            Map<String, Object> result = invokeMap(agent, "test_multi_turn", "calculate (1+2) * 3");

            assertThat(result).containsEntry("result_type", "answer");
            assertThat(String.valueOf(result.get("output"))).contains("9");
            assertThat(addTool.invokeCount()).isEqualTo(1);
            assertThat(multiplyTool.invokeCount()).isEqualTo(1);
            assertThat(((ScriptedModelClient) agent.getLlm()).callCount()).isEqualTo(3);
        } finally {
            Runner.resourceMgr.removeTool(addToolId);
            Runner.resourceMgr.removeTool(multiplyToolId);
        }
    }

    @Test
    void testReactAgentPureConversation() {
        String addToolId = unique("add");
        CountingTool addTool = new CountingTool(addToolId, "add", (a, b) -> a + b);
        LegacyReActAgent agent = agent(
                "react_agent_conversation",
                addTool,
                textResponse("hello, how can I help?")
        );

        try {
            Map<String, Object> result = invokeMap(agent, "test_conversation", "hello");

            assertThat(result).containsEntry("result_type", "answer");
            assertThat(String.valueOf(result.get("output"))).contains("hello");
            assertThat(addTool.invokeCount()).isZero();
            assertThat(((ScriptedModelClient) agent.getLlm()).callCount()).isEqualTo(1);
        } finally {
            Runner.resourceMgr.removeTool(addToolId);
        }
    }

    private static LegacyReActAgent agent(String agentId, Tool tool, AssistantMessage... responses) {
        return agent(agentId, List.of(tool), responses);
    }

    private static LegacyReActAgent agent(String agentId, List<Tool> tools, AssistantMessage... responses) {
        LegacyReActAgent agent = new LegacyReActAgent(config(agentId));
        agent.addTools(tools);
        agent.setLlm(new ScriptedModelClient(List.of(responses)));
        return agent;
    }

    private static LegacyReActAgentConfig config(String agentId) {
        ModelConfig model = ModelConfig.builder()
                .modelProvider("OpenAI")
                .modelInfo(BaseModelInfo.builder()
                        .modelName("gpt-3.5-turbo")
                        .apiBase("mock_url")
                        .apiKey("mock_key")
                        .temperature(0.7f)
                        .topP(0.9f)
                        .timeout(30)
                        .build())
                .build();
        return LegacyReActAgentFactory.createReactAgentConfig(
                agentId,
                "0.0.1",
                "math assistant",
                model,
                List.of(Map.of("role", "system", "content", "You are a math assistant."))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(LegacyReActAgent agent, String sessionId, String query) {
        return (Map<String, Object>) agent.invoke(
                Map.of("conversation_id", sessionId, "query", query),
                new MemorySession(sessionId)
        ).toCompletableFuture().join();
    }

    private static AssistantMessage textResponse(String content) {
        return new AssistantMessage(content);
    }

    private static AssistantMessage toolResponse(String name, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(unique("call"))
                        .type("function")
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private interface BinaryOperation {
        int apply(int a, int b);
    }

    /**
     * Mirrors Python's local add/multiply {@code LocalFunction} tools in
     * {@code tests/unit_tests/agent/react_agent/test_react_agent_mock.py}.
     */
    private static final class CountingTool extends Tool {
        private final BinaryOperation operation;
        private int invokeCount;

        private CountingTool(String id, String name, BinaryOperation operation) {
            super(new ToolCard(id, name, name + " operation", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "a", Map.of("type", "number"),
                            "b", Map.of("type", "number")
                    ),
                    "required", List.of("a", "b")
            )));
            this.operation = operation;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            Number a = (Number) inputs.get("a");
            Number b = (Number) inputs.get("b");
            return operation.apply(a.intValue(), b.intValue());
        }

        private int invokeCount() {
            return invokeCount;
        }
    }

    private static final class ScriptedModelClient extends Model {
        private final Queue<AssistantMessage> responses;
        private int callCount;

        private ScriptedModelClient(List<AssistantMessage> responses) {
            super((messages, options) -> null);
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public java.util.concurrent.CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                com.openjiuwen.core.foundation.llm.ModelInvokeOptions options
        ) {
            callCount++;
            return java.util.concurrent.CompletableFuture.completedFuture(responses.remove());
        }

        private int callCount() {
            return callCount;
        }
    }

    private static final class MemorySession implements AgentSessionApi,
            com.openjiuwen.core.context_engine.ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new java.util.ArrayList<>();

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

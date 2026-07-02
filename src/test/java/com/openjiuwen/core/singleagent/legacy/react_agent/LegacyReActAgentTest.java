/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.react_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the legacy ReAct agent compatibility layer.
 *
 * <p>Mirrors Python's {@code LegacyReActAgent} and
 * {@code create_react_agent_config} in
 * {@code openjiuwen/core/single_agent/legacy/react_agent.py}.</p>
 */
class LegacyReActAgentTest {

    @Test
    void createReactAgentConfigPreservesInputsAndReturnsLegacyConfig() {
        ModelConfig model = ModelConfig.builder()
                .modelProvider("openai")
                .modelInfo(BaseModelInfo.builder().modelName("demo-model").build())
                .build();

        LegacyReActAgentConfig config = LegacyReActAgentFactory.createReactAgentConfig(
                "agent",
                "1.0",
                "desc",
                model,
                List.of(Map.<String, Object>of("role", "system", "content", "guide"))
        );

        assertEquals("agent", config.getId());
        assertEquals("1.0", config.getVersion());
        assertEquals("desc", config.getDescription());
        assertEquals(model, config.getModel());
        assertEquals(List.of(Map.<String, Object>of("role", "system", "content", "guide")),
                config.getPromptTemplate());
    }

    @Test
    void invokeAddsFirstUserMessageAndReturnsAnswerWhenModelHasNoToolCalls() {
        LegacyReActAgentConfig config = baseConfig("agent-model");
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        LegacyReActAgent agent = new LegacyReActAgent(config);
        agent.setLlm(new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(messages);
            return CompletableFuture.completedFuture(AssistantMessage.builder()
                    .content("final answer")
                    .toolCalls(List.of())
                    .build());
        }));

        Object result = agent.invoke(Map.<String, Object>of("query", "hello"),
                new FakeSession("s1")).toCompletableFuture().join();

        assertInstanceOf(Map.class, result);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertEquals("final answer", resultMap.get("output"));
        assertEquals("answer", resultMap.get("result_type"));
        assertFalse(capturedMessages.isEmpty());
        List<BaseMessage> messages = capturedMessages.get(0);
        assertEquals("system", messages.get(0).getRole());
        assertEquals("guide", messages.get(0).getContent());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("hello", messages.get(1).getContent());
    }

    @Test
    void executeToolCallParsesJsonArgumentsAndStoresToolMessage() {
        String toolName = "legacy-react-echo-" + UUID.randomUUID();
        LegacyReActAgentConfig config = baseConfig("agent-tool-" + UUID.randomUUID());
        LegacyReActAgent agent = new LegacyReActAgent(config);
        EchoTool tool = new EchoTool(toolName);
        FakeSession session = new FakeSession("s2");

        agent.addTools(List.of(tool));
        Object result = agent.executeToolCall(
                ToolCall.builder().id("call-1").name(toolName).arguments("{\"value\":\"abc\"}").build(),
                session
        ).toCompletableFuture().join();

        assertEquals("abc", result);
        assertEquals(Map.of("value", "abc"), tool.lastInputs);
        assertEquals(1, agent.getContextEngine()
                .getContext(com.openjiuwen.core.context_engine.ContextEngine.DEFAULT_CONTEXT_ID, "s2")
                .getMessages(null, true)
                .stream()
                .filter(message -> "tool".equals(message.getRole()))
                .count());
    }

    @Test
    void reactAliasExtendsLegacyAgentAndNoQueryReturnsPythonErrorPayload() {
        ReActAgent alias = new ReActAgent(baseConfig("agent-alias"));

        Object result = alias.invoke(Map.of(), new FakeSession("s3")).toCompletableFuture().join();

        assertTrue(alias instanceof LegacyReActAgent);
        assertInstanceOf(Map.class, result);
        assertEquals("No query provided", ((Map<?, ?>) result).get("output"));
        assertEquals("error", ((Map<?, ?>) result).get("result_type"));
    }

    @Test
    void buildModelPropagatesHttpVersionFromModelInfo() {
        ModelConfig model = ModelConfig.builder()
                .modelProvider("openai")
                .modelInfo(BaseModelInfo.builder()
                        .apiKey("key")
                        .apiBase("base")
                        .modelName("demo-model")
                        .httpVersion(ModelHttpVersion.HTTP_1_1)
                        .build())
                .build();
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId("agent-http-version");
        config.setDescription("description");
        config.setModel(model);

        LegacyReActAgent agent = new LegacyReActAgent(config);

        assertEquals(ModelHttpVersion.HTTP_1_1, agent.getLlm().getModelClientConfig().getHttpVersion());
    }

    static LegacyReActAgentConfig baseConfig(String agentId) {
        ModelConfig model = ModelConfig.builder()
                .modelProvider("openai")
                .modelInfo(BaseModelInfo.builder()
                        .apiKey("key")
                        .apiBase("base")
                        .modelName("demo-model")
                        .build())
                .build();
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(agentId);
        config.setDescription("description");
        config.setModel(model);
        config.setPromptTemplate(List.of(Map.<String, Object>of("role", "system", "content", "guide")));
        return config;
    }

    static final class EchoTool extends Tool {
        private Map<String, Object> lastInputs = new LinkedHashMap<>();

        EchoTool(String name) {
            super(new ToolCard(name, name, "echo"));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            lastInputs = new LinkedHashMap<>(inputs);
            return inputs.get("value");
        }
    }

    static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> chunks = new ArrayList<>();

        FakeSession(String sessionId) {
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
            chunks.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return chunks.iterator();
        }
    }
}

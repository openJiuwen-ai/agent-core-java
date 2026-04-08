/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Operator module (LLMCallOperator, ToolCallOperator).
 * Corresponds to Python operator usage within ReAct agent workflows.
 */
@Tag("system-test")
class OperatorSystemTest {

    private static Model model;

    @BeforeAll
    static void setUp() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ApiConfigLoader.getModelProvider())
                .apiKey(ApiConfigLoader.getApiKey())
                .apiBase(ApiConfigLoader.getApiBase())
                .timeout(60.0)
                .maxRetries(2)
                .verifySsl(ApiConfigLoader.getSslVerify())
                .sslCert(ApiConfigLoader.getSslCert())
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ApiConfigLoader.getModelName())
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(512)
                .build();

        model = new Model(clientConfig, requestConfig);
    }

    @Test
    @DisplayName("LLMCallOperator invoke with system and user prompt")
    void testLlmCallOperatorInvoke() throws Exception {
        LLMCallOperator operator = new LLMCallOperator(
                ApiConfigLoader.getModelName(),
                model,
                "你是一个有用的AI助手。",
                "{{query}}");

        Map<String, Object> inputs = Map.of("query", "请用一句话解释什么是面向对象编程。");
        Object result = operator.invoke(inputs, new MinimalSession());

        assertNotNull(result, "Operator result should not be null");
        assertTrue(result instanceof AssistantMessage, "Result should be AssistantMessage");
        AssistantMessage msg = (AssistantMessage) result;
        assertNotNull(msg.getContentAsString());
        assertFalse(msg.getContentAsString().isBlank());
        System.out.println("[LLMCallOperator] Response: " + msg.getContentAsString());
    }

    @Test
    @DisplayName("LLMCallOperator with frozen system prompt")
    void testLlmCallOperatorFrozenPrompt() throws Exception {
        LLMCallOperator operator = new LLMCallOperator(
                ApiConfigLoader.getModelName(),
                model,
                "你是一个翻译助手，只做英文到中文的翻译。",
                "请翻译：{{query}}",
                true, true, "llm_call_frozen", null);

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertTrue(tunables.isEmpty(), "Frozen prompts should have no tunables");

        Map<String, Object> inputs = Map.of("query", "Hello World");
        Object result = operator.invoke(inputs, new MinimalSession());

        assertNotNull(result);
        AssistantMessage msg = (AssistantMessage) result;
        assertNotNull(msg.getContentAsString());
        System.out.println("[LLMCallOperator Frozen] Response: " + msg.getContentAsString());
    }

    @Test
    @DisplayName("LLMCallOperator state snapshot and restore")
    void testLlmCallOperatorState() {
        LLMCallOperator operator = new LLMCallOperator(
                ApiConfigLoader.getModelName(),
                model,
                "System prompt",
                "User prompt: {{query}}");

        Map<String, Object> state = operator.getState();
        assertNotNull(state);
        System.out.println("[LLMCallOperator State] " + state);

        // Restore state
        operator.loadState(state);
        Map<String, Object> stateAfterReload = operator.getState();
        assertNotNull(stateAfterReload);
    }

    @Test
    @DisplayName("ToolCallOperator with LocalFunction tool")
    void testToolCallOperatorInvoke() throws Exception {
        ToolCard card = ToolCard.builder()
                .id("multiply")
                .name("multiply")
                .description("Multiplies two numbers")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("type", "number"),
                                "b", Map.of("type", "number")),
                        "required", java.util.List.of("a", "b")))
                .build();

        LocalFunction tool = new LocalFunction(card, inputs -> {
            Number a = (Number) inputs.get("a");
            Number b = (Number) inputs.get("b");
            return a.doubleValue() * b.doubleValue();
        });

        ToolCallOperator operator = new ToolCallOperator(tool);
        Map<String, Object> inputs = Map.of("a", 7, "b", 8);
        Object result = operator.invoke(inputs, new MinimalSession());

        assertNotNull(result);
        System.out.println("[ToolCallOperator] Result: " + result);
    }

    @Test
    @DisplayName("LLMCallOperator tunable parameters")
    void testLlmCallOperatorTunables() {
        LLMCallOperator operator = new LLMCallOperator(
                ApiConfigLoader.getModelName(),
                model,
                "System: {{role}}",
                "{{query}}",
                false, true, "llm_tunables", null);

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertNotNull(tunables);
        assertTrue(tunables.containsKey("system_prompt"),
                "Non-frozen system prompt should be tunable");
        assertFalse(tunables.containsKey("user_prompt"),
                "Frozen user prompt should not be tunable");
        System.out.println("[LLMCallOperator Tunables] " + tunables.keySet());
    }

    /**
     * Minimal Session implementation for operator testing.
     */
    static class MinimalSession implements Session {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private String currentOperatorId;

        @Override
        public String getSessionId() {
            return "test-session-" + System.currentTimeMillis();
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> stateMap) {
            if (stateMap != null) {
                state.putAll(stateMap);
            }
        }

        @Override
        public void setCurrentOperatorId(String operatorId) {
            this.currentOperatorId = operatorId;
        }

        @Override
        public String getCurrentOperatorId() {
            return currentOperatorId;
        }
    }
}

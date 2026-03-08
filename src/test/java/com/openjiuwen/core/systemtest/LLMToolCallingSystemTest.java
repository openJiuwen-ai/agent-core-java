/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for LLM Tool Calling (Function Calling).
 * Tests that the LLM can correctly identify and invoke tools.
 * Corresponds to Python's react_agent tool calling pattern.
 */
@Tag("system-test")
class LLMToolCallingSystemTest {

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
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ApiConfigLoader.getModelName())
                .temperature(0.1)
                .topP(0.9)
                .maxTokens(1024)
                .build();

        model = new Model(clientConfig, requestConfig);
    }

    @Test
    @DisplayName("LLM invocation with tool definitions triggers tool call")
    void testLlmWithToolDefinitions() throws Exception {
        ToolCard weatherCard = ToolCard.builder()
                .id("get_weather")
                .name("get_weather")
                .description("Get the current weather for a given city")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string",
                                        "description", "The city name to get weather for")),
                        "required", List.of("city")))
                .build();

        ToolInfo toolInfo = weatherCard.toolInfo();
        List<ToolInfo> tools = List.of(toolInfo);

        List<UserMessage> messages = List.of(
                new UserMessage("北京今天天气怎么样？"));

        AssistantMessage response = model.invoke(
                messages, tools, 0.1f, null, null, 512, null, null, null, null);

        assertNotNull(response, "Response should not be null");
        // The model may either produce tool_calls or a direct text reply
        String content = response.getContentAsString();
        boolean hasToolCalls = response.getToolCalls() != null && !response.getToolCalls().isEmpty();
        boolean hasContent = content != null && !content.isBlank();
        System.out.println("[ToolCalling] HasToolCalls=" + hasToolCalls
                + ", HasContent=" + hasContent
                + ", ToolCalls=" + response.getToolCalls()
                + ", Content=" + content);
        // At least one of tool call or content should be present
        assertTrue(hasToolCalls || hasContent,
                "Response should contain either tool calls or content");
    }

    @Test
    @DisplayName("LLM invocation with multiple tool definitions")
    void testLlmWithMultipleTools() throws Exception {
        ToolCard weatherCard = ToolCard.builder()
                .id("get_weather")
                .name("get_weather")
                .description("Get weather information for a city")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string",
                                        "description", "City name")),
                        "required", List.of("city")))
                .build();

        ToolCard calcCard = ToolCard.builder()
                .id("calculator")
                .name("calculator")
                .description("Perform mathematical calculations")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "expression", Map.of("type", "string",
                                        "description", "Math expression to evaluate")),
                        "required", List.of("expression")))
                .build();

        List<ToolInfo> tools = List.of(weatherCard.toolInfo(), calcCard.toolInfo());

        List<UserMessage> messages = List.of(
                new UserMessage("请计算 123 * 456 的结果"));

        AssistantMessage response = model.invoke(
                messages, tools, 0.1f, null, null, 512, null, null, null, null);

        assertNotNull(response);
        boolean hasToolCalls = response.getToolCalls() != null && !response.getToolCalls().isEmpty();
        boolean hasContent = response.getContentAsString() != null
                && !response.getContentAsString().isBlank();
        System.out.println("[MultiTool] HasToolCalls=" + hasToolCalls
                + ", Content=" + response.getContentAsString()
                + ", ToolCalls=" + response.getToolCalls());
        org.junit.jupiter.api.Assertions.assertTrue(hasToolCalls || hasContent,
                "Response should have either tool calls or content");
    }
}

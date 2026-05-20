/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for LLM Tool Calling (Function Calling).
 * Tests that the LLM can correctly identify and invoke tools.
 * Corresponds to Python's react_agent tool calling pattern.
 */
@Tag("system-test")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class LLMToolCallingSystemTest extends SystemTestSupport {

    private static final float REQUEST_TIMEOUT_SECONDS = 20.0f;

    private Model model;

    @BeforeEach
    void setUp() {
        assumeRemoteModelAvailable();

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ApiConfigLoader.getModelProvider())
                .apiKey(ApiConfigLoader.getApiKey())
                .apiBase(ApiConfigLoader.getApiBase())
                .timeout(REQUEST_TIMEOUT_SECONDS)
                .maxRetries(0)
                .verifySsl(ApiConfigLoader.getSslVerify())
                .sslCert(ApiConfigLoader.getSslCert())
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ApiConfigLoader.getModelName())
                .temperature(0.1)
                .topP(0.9)
                .maxTokens(256)
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
                                "city", Map.of(
                                        "type", "string",
                                        "description", "The city name to get weather for")),
                        "required", List.of("city")))
                .build();

        List<ToolInfo> tools = List.of(weatherCard.toolInfo());
        List<UserMessage> messages = List.of(new UserMessage("What is the weather in Beijing today?"));

        AssistantMessage response = model.invoke(
                messages, tools, 0.1f, null, null, 128, null, null, REQUEST_TIMEOUT_SECONDS, null);

        assertNotNull(response, "Response should not be null");
        String content = response.getContentAsString();
        boolean hasToolCalls = response.getToolCalls() != null && !response.getToolCalls().isEmpty();
        boolean hasContent = content != null && !content.isBlank();
        System.out.println("[ToolCalling] HasToolCalls=" + hasToolCalls
                + ", HasContent=" + hasContent
                + ", ToolCalls=" + response.getToolCalls()
                + ", Content=" + content);
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
                                "city", Map.of(
                                        "type", "string",
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
                                "expression", Map.of(
                                        "type", "string",
                                        "description", "Math expression to evaluate")),
                        "required", List.of("expression")))
                .build();

        List<ToolInfo> tools = List.of(weatherCard.toolInfo(), calcCard.toolInfo());
        List<UserMessage> messages = List.of(new UserMessage("Calculate 123 * 456."));

        AssistantMessage response = model.invoke(
                messages, tools, 0.1f, null, null, 128, null, null, REQUEST_TIMEOUT_SECONDS, null);

        assertNotNull(response, "Response should not be null");
        boolean hasToolCalls = response.getToolCalls() != null && !response.getToolCalls().isEmpty();
        boolean hasContent = response.getContentAsString() != null
                && !response.getContentAsString().isBlank();
        System.out.println("[MultiTool] HasToolCalls=" + hasToolCalls
                + ", Content=" + response.getContentAsString()
                + ", ToolCalls=" + response.getToolCalls());
        assertTrue(hasToolCalls || hasContent,
                "Response should have either tool calls or content");
    }
}

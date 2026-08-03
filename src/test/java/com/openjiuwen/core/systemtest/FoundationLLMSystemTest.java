/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Foundation LLM module.
 * Tests real LLM invocation (invoke and stream) using remote API.
 * Corresponds to Python's build_react_agent example (LLM call portion).
 */
@Tag("system-test")
class FoundationLLMSystemTest {

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
    @DisplayName("LLM invoke returns non-empty response")
    void testLlmInvoke() throws Exception {
        List<UserMessage> messages = List.of(new UserMessage("请用一句话介绍你自己。"));

        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .maxTokens(256)
                .build();
        AssistantMessage response = model.invoke(
                (List<BaseMessage>) (List<?>) messages, options).toCompletableFuture().join();

        assertNotNull(response, "LLM response should not be null");
        String content = response.getContentAsString();
        assertNotNull(content, "Response content should not be null");
        assertFalse(content.isBlank(), "Response content should not be blank");
        System.out.println("[LLM Invoke] Response: " + content);
    }

    @Test
    @DisplayName("LLM invoke with system prompt")
    void testLlmInvokeWithSystemPrompt() throws Exception {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(new com.openjiuwen.core.foundation.llm.schema.SystemMessage(
                "你是一个专业的Java开发助手。"));
        messages.add(new UserMessage("Java中如何创建线程？请简要回答。"));

        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .temperature(0.6f)
                .topP(0.8f)
                .maxTokens(256)
                .build();
        AssistantMessage response = model.invoke(messages, options).toCompletableFuture().join();

        assertNotNull(response, "LLM response should not be null");
        String content = response.getContentAsString();
        assertNotNull(content, "Response content should not be null");
        assertFalse(content.isBlank(), "Response content should not be blank");
        assertTrue(content.contains("Thread") || content.contains("线程") || content.contains("thread"),
                "Response should mention Thread/线程");
        System.out.println("[LLM SystemPrompt] Response: " + content);
    }

    @Test
    @DisplayName("LLM stream returns non-empty chunks")
    void testLlmStream() throws Exception {
        List<UserMessage> messages = List.of(new UserMessage("用三句话描述春天。"));

        ModelInvokeOptions streamOptions = ModelInvokeOptions.builder()
                .maxTokens(256)
                .build();
        Iterator<AssistantMessageChunk> stream = model.stream(
                (List<BaseMessage>) (List<?>) messages, streamOptions);

        assertNotNull(stream, "Stream should not be null");
        StringBuilder fullResponse = new StringBuilder();
        int chunkCount = 0;
        while (stream.hasNext()) {
            AssistantMessageChunk chunk = stream.next();
            if (chunk != null && chunk.getContentAsString() != null) {
                fullResponse.append(chunk.getContentAsString());
                chunkCount++;
            }
        }
        assertTrue(chunkCount > 0, "Should receive at least one chunk");
        assertFalse(fullResponse.toString().isBlank(), "Assembled response should not be blank");
        System.out.println("[LLM Stream] Chunks: " + chunkCount
                + ", Response: " + fullResponse);
    }

    @Test
    @DisplayName("LLM invoke with temperature control")
    void testLlmTemperatureControl() throws Exception {
        List<UserMessage> messages = List.of(new UserMessage("1+1等于几？只回答数字。"));

        ModelInvokeOptions tempOptions = ModelInvokeOptions.builder()
                .temperature(0.01f)
                .topP(0.1f)
                .maxTokens(32)
                .build();
        AssistantMessage response = model.invoke(
                (List<BaseMessage>) (List<?>) messages, tempOptions).toCompletableFuture().join();

        assertNotNull(response);
        String content = response.getContentAsString();
        assertNotNull(content);
        assertTrue(content.contains("2"), "Low-temperature response should contain '2'");
        System.out.println("[LLM Temperature] Response: " + content);
    }

    @Test
    @DisplayName("LLM multi-turn conversation")
    void testLlmMultiTurnConversation() throws Exception {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("我的名字是小明。请记住它。"));

        ModelInvokeOptions multiOptions = ModelInvokeOptions.builder()
                .temperature(0.7f)
                .topP(0.9f)
                .maxTokens(128)
                .build();
        AssistantMessage first = model.invoke(messages, multiOptions).toCompletableFuture().join();
        assertNotNull(first);

        messages.add(first);
        messages.add(new UserMessage("我叫什么名字？"));

        AssistantMessage second = model.invoke(messages, multiOptions).toCompletableFuture().join();
        assertNotNull(second);
        String content = second.getContentAsString();
        assertNotNull(content);
        assertTrue(content.contains("小明"), "LLM should recall the name '小明'");
        System.out.println("[LLM MultiTurn] Response: " + content);
    }
}

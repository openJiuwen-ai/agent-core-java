/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mock LLM model for unit testing.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.fixtures.mock_llm.MockLLMModel}.
 */
public class MockLLMModel extends BaseModelClient {

    public static final String CLIENT_NAME = "mock";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private int callCount;
    private List<AssistantMessage> responses;
    private List<List<Object>> callHistory;

    public MockLLMModel() {
        this(defaultModelConfig(), defaultClientConfig());
    }

    public MockLLMModel(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.callCount = 0;
        this.responses = new ArrayList<>();
        this.callHistory = new ArrayList<>();
    }

    @Override
    protected String getClientName() {
        return CLIENT_NAME;
    }

    /**
     * Set predefined responses for the mock model.
     */
    public void setResponses(List<AssistantMessage> responses) {
        this.responses = responses == null ? new ArrayList<>() : new ArrayList<>(responses);
        this.callCount = 0;
        this.callHistory = new ArrayList<>();
    }

    /**
     * Get the next response from the predefined list.
     */
    public AssistantMessage getNextResponse() {
        if (callCount < responses.size()) {
            AssistantMessage response = responses.get(callCount);
            callCount++;
            return response;
        }
        return AssistantMessage.builder()
                .content("Default mock response")
                .build();
    }

    /**
     * Get call count.
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * Get call history.
     */
    public List<List<Object>> getCallHistory() {
        return callHistory;
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                   Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                   Map<String, Object> kwargs) {
        callHistory.add(normalizeMessages(messages));
        return getNextResponse();
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                  String model, Integer maxTokens, String stop,
                                                  BaseOutputParser outputParser, Float timeout,
                                                  Map<String, Object> kwargs) {
        callHistory.add(normalizeMessages(messages));
        AssistantMessage result = getNextResponse();
        AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content(result.getContent())
                .toolCalls(result.getToolCalls())
                .usageMetadata(result.getUsageMetadata())
                .finishReason(result.getFinishReason())
                .parserContent(result.getParserContent())
                .reasoningContent(result.getReasoningContent())
                .build();
        return List.of(chunk).iterator();
    }

    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                 String negativePrompt, int n, boolean promptExtend,
                                                 boolean watermark, int seed, Map<String, Object> kwargs) {
        int count = Math.max(1, n);
        List<String> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            images.add("mock://image/" + i);
        }
        return ImageGenerationResponse.builder()
                .model(resolveModel(model))
                .images(images)
                .imagesBase64(List.of())
                .build();
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                  String languageType, Map<String, Object> kwargs) {
        return AudioGenerationResponse.builder()
                .model(resolveModel(model))
                .audioUrl("mock://audio/0")
                .audioData(new byte[0])
                .duration(0.0)
                .build();
    }

    @Override
    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                 String model, String size, String resolution, int duration,
                                                 boolean promptExtend, boolean watermark, String negativePrompt,
                                                 Integer seed, Map<String, Object> kwargs) {
        return VideoGenerationResponse.builder()
                .model(resolveModel(model))
                .videoUrl("mock://video/0")
                .videoData(new byte[0])
                .duration((double) Math.max(0, duration))
                .resolution(resolution)
                .build();
    }

    /**
     * Create a text response AssistantMessage.
     */
    public static AssistantMessage createTextResponse(String content) {
        return createTextResponse(content, "mock-model");
    }

    public static AssistantMessage createTextResponse(String content, String modelName) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder().modelName(modelName).build())
                .build();
    }

    public static AssistantMessage createTextResponse(String content, String modelName, String ignoredFinishReason) {
        return createTextResponse(content, modelName);
    }

    /**
     * Create a tool call response AssistantMessage.
     */
    public static AssistantMessage createToolCallResponse(String toolName, String arguments) {
        return createToolCallResponse(toolName, arguments, null, "mock-model");
    }

    public static AssistantMessage createToolCallResponse(String toolName, String arguments, String toolCallId,
                                                          String modelName) {
        String resolvedToolCallId = toolCallId != null ? toolCallId : "mock_call_" + toolName;
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(resolvedToolCallId)
                        .type("function")
                        .name(toolName)
                        .arguments(arguments)
                        .build()))
                .usageMetadata(UsageMetadata.builder().modelName(modelName).build())
                .finishReason("tool_calls")
                .build();
    }

    /**
     * Create a JSON response AssistantMessage.
     */
    public static AssistantMessage createJsonResponse(Map<String, Object> data) {
        return createJsonResponse(data, "mock-model");
    }

    public static AssistantMessage createJsonResponse(Map<String, Object> data, String modelName) {
        try {
            return createTextResponse(JSON_MAPPER.writeValueAsString(data), modelName, "stop");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize mock JSON response", e);
        }
    }

    private String resolveModel(String model) {
        if (model != null) {
            return model;
        }
        return modelConfig != null ? modelConfig.getModelName() : "mock-model";
    }

    private static ModelRequestConfig defaultModelConfig() {
        return ModelRequestConfig.builder()
                .modelName("mock-model")
                .build();
    }

    private static ModelClientConfig defaultClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .clientId("mock")
                .apiKey("mock-api-key")
                .apiBase("http://mock-api-base")
                .verifySsl(false)
                .build();
    }

    private static List<Object> normalizeMessages(Object messages) {
        if (messages instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (messages instanceof BaseMessage) {
            return List.of(messages);
        }
        return List.of(messages);
    }
}

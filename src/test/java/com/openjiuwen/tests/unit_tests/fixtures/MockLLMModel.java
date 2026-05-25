/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.fixtures;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock LLM model for testing.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.fixtures.mock_llm.MockLLMModel}.
 */
public class MockLLMModel {

    private int callCount = 0;
    private List<AssistantMessage> responses = new ArrayList<>();
    private List<List<Map<String, Object>>> callHistory = new ArrayList<>();

    public MockLLMModel() {
    }

    /**
     * Create a text response AssistantMessage.
     */
    public static AssistantMessage createTextResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .build();
    }

    /**
     * Create a tool call response AssistantMessage.
     */
    public static AssistantMessage createToolCallResponse(String toolName, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(com.openjiuwen.core.foundation.llm.schema.ToolCall.builder()
                        .id("mock_call")
                        .type("function")
                        .name(toolName)
                        .arguments(arguments)
                        .build()))
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .finishReason("tool_calls")
                .build();
    }

    /**
     * Set predefined responses for the mock model.
     */
    public void setResponses(List<AssistantMessage> responses) {
        this.responses = new ArrayList<>(responses);
        this.callCount = 0;
        this.callHistory.clear();
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
        return createTextResponse("Default mock response");
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
    public List<List<Map<String, Object>>> getCallHistory() {
        return callHistory;
    }
}
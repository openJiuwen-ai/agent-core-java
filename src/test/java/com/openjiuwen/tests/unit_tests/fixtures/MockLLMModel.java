/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.fixtures;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code tests.unit_tests.fixtures.mock_llm.MockLLMModel}.
 */
public class MockLLMModel {

    private int callCount;
    private List<AssistantMessage> responses;
    private final List<List<Object>> callHistory;

    public MockLLMModel() {
        this.callCount = 0;
        this.responses = new ArrayList<>();
        this.callHistory = new ArrayList<>();
    }

    public void setResponses(List<AssistantMessage> responses) {
        this.responses = new ArrayList<>(responses);
        this.callCount = 0;
        this.callHistory.clear();
    }

    private AssistantMessage getNextResponse() {
        if (callCount < responses.size()) {
            AssistantMessage response = responses.get(callCount);
            callCount++;
            return response;
        }
        return createTextResponse("Default mock response");
    }

    public AssistantMessage invoke(Object messages, Object tools) {
        if (messages instanceof List) {
            callHistory.add((List<Object>) messages);
        } else {
            List<Object> wrapper = new ArrayList<>();
            wrapper.add(messages);
            callHistory.add(wrapper);
        }
        return getNextResponse();
    }

    public AssistantMessage invoke(Object messages) {
        return invoke(messages, null);
    }

    public Iterable<AssistantMessage> stream(Object messages, Object tools) {
        if (messages instanceof List) {
            callHistory.add((List<Object>) messages);
        } else {
            List<Object> wrapper = new ArrayList<>();
            wrapper.add(messages);
            callHistory.add(wrapper);
        }
        AssistantMessage result = getNextResponse();
        List<AssistantMessage> list = new ArrayList<>();
        list.add(result);
        return list;
    }

    public Iterable<AssistantMessage> stream(Object messages) {
        return stream(messages, null);
    }

    public int getCallCount() {
        return callCount;
    }

    public List<List<Object>> getCallHistory() {
        return callHistory;
    }

    public static AssistantMessage createTextResponse(String content) {
        return createTextResponse(content, "mock-model", "stop");
    }

    public static AssistantMessage createTextResponse(String content, String modelName, String finishReason) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder()
                        .modelName(modelName)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    public static AssistantMessage createToolCallResponse(String toolName, String arguments) {
        return createToolCallResponse(toolName, arguments, null, "mock-model");
    }

    public static AssistantMessage createToolCallResponse(String toolName, String arguments, String toolCallId, String modelName) {
        if (toolCallId == null) {
            toolCallId = "mock_call_" + toolName;
        }
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        ToolCall.builder()
                                .id(toolCallId)
                                .type("function")
                                .name(toolName)
                                .arguments(arguments)
                                .build()
                ))
                .usageMetadata(UsageMetadata.builder()
                        .modelName(modelName)
                        .finishReason("tool_calls")
                        .build())
                .build();
    }

    public static AssistantMessage createJsonResponse(java.util.Map<String, Object> data) {
        return createJsonResponse(data, "mock-model");
    }

    public static AssistantMessage createJsonResponse(java.util.Map<String, Object> data, String modelName) {
        return AssistantMessage.builder()
                .content(toJson(data))
                .usageMetadata(UsageMetadata.builder()
                        .modelName(modelName)
                        .finishReason("stop")
                        .build())
                .build();
    }

    private static String toJson(java.util.Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}

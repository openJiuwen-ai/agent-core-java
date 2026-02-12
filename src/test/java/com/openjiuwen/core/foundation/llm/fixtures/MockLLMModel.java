// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.fixtures;

import com.openjiuwen.core.foundation.llm.modelclients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.outputparsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Mock LLM Model for unit testing.
 * 
 * 对应 Python: agent-core/tests/unit_tests/fixtures/mock_llm.py
 * 
 * 提供预定义响应的模拟LLM实现，支持快速可靠的单元测试，无需实际API调用。
 */
public class MockLLMModel extends BaseModelClient {

    private int callCount = 0;
    private List<AssistantMessage> responses = new ArrayList<>();
    private final List<Object> callHistory = new ArrayList<>();

    /**
     * 使用默认配置创建MockLLMModel
     */
    public MockLLMModel() {
        this(
                new ModelRequestConfig.Builder().modelName("mock-model").build(),
                new ModelClientConfig.Builder()
                        .clientProvider("OpenAI")
                        .clientId("mock-client")
                        .apiKey("mock-api-key")
                        .apiBase("http://mock-api-base")
                        .verifySsl(false)
                        .build()
        );
    }

    /**
     * 使用指定配置创建MockLLMModel
     */
    public MockLLMModel(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    /**
     * 设置预定义响应
     * 
     * @param responses 响应列表，按顺序返回
     */
    public void setResponses(List<AssistantMessage> responses) {
        this.responses = new ArrayList<>(responses);
        this.callCount = 0;
        this.callHistory.clear();
    }

    /**
     * 获取下一个响应
     */
    private AssistantMessage getNextResponse() {
        if (callCount < responses.size()) {
            return responses.get(callCount++);
        }
        return new AssistantMessage.Builder().content("Default mock response").build();
    }

    /**
     * 获取调用历史
     */
    public List<Object> getCallHistory() {
        return new ArrayList<>(callHistory);
    }

    /**
     * 获取调用次数
     */
    public int getCallCount() {
        return callCount;
    }

    @Override
    public CompletableFuture<AssistantMessage> invoke(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        callHistory.add(messages);
        return CompletableFuture.completedFuture(getNextResponse());
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        callHistory.add(messages);
        AssistantMessage result = getNextResponse();
        
        // 将AssistantMessage转换为AssistantMessageChunk用于流式返回
        AssistantMessageChunk chunk = new AssistantMessageChunk.Builder()
                .content(result.getContent())
                .toolCalls(result.getToolCalls())
                .usageMetadata(result.getUsageMetadata())
                .build();
        
        return Collections.singletonList(chunk).iterator();
    }

    /**
     * 创建文本响应
     */
    public static AssistantMessage createTextResponse(String content) {
        return createTextResponse(content, "mock-model", "stop");
    }

    /**
     * 创建文本响应
     */
    public static AssistantMessage createTextResponse(String content, String modelName, String finishReason) {
        UsageMetadata usage = new UsageMetadata();
        usage.setModelName(modelName);
        
        return new AssistantMessage.Builder()
                .content(content)
                .usageMetadata(usage)
                .finishReason(finishReason)
                .build();
    }

    /**
     * 创建工具调用响应
     */
    public static AssistantMessage createToolCallResponse(String toolName, String arguments) {
        return createToolCallResponse(toolName, arguments, null, "mock-model");
    }

    /**
     * 创建工具调用响应
     */
    public static AssistantMessage createToolCallResponse(
            String toolName,
            String arguments,
            String toolCallId,
            String modelName
    ) {
        if (toolCallId == null) {
            toolCallId = "mock_call_" + toolName;
        }

        ToolCall toolCall = new ToolCall(toolCallId, "function", toolName, arguments, null);
        
        UsageMetadata usage = new UsageMetadata();
        usage.setModelName(modelName);

        return new AssistantMessage.Builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .usageMetadata(usage)
                .finishReason("tool_calls")
                .build();
    }
}


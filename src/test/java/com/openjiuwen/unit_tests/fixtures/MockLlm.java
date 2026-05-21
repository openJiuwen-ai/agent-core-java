/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.fixtures;

import com.openjiuwen.core.foundation.llm.*;
import com.openjiuwen.core.foundation.llm.schema.message.*;
import com.openjiuwen.core.foundation.tool.ToolInfo;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock LLM Model for unit testing.
 * <p>
 * This module provides a mock LLM implementation that returns predefined
 * responses, enabling fast and reliable unit tests without real API calls.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/fixtures/mock_llm.py}.
 */
public class MockLlm {

    private int callCount = 0;
    private List<AssistantMessage> responses = new ArrayList<>();
    private List<List<BaseMessage>> callHistory = new ArrayList<>();
    private AssistantMessage defaultResponse;

    /**
     * Initialize MockLLMModel.
     */
    public MockLlm() {
        this.defaultResponse = createTextResponse("Default mock response");
    }

    /**
     * Set predefined responses.
     *
     * @param responses List of AssistantMessage responses to return
     */
    public void setResponses(List<AssistantMessage> responses) {
        this.responses = new ArrayList<>(responses);
        this.callCount = 0;
    }

    /**
     * Get call count.
     *
     * @return Number of times the model has been called
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * Get call history.
     *
     * @return List of messages received in each call
     */
    public List<List<BaseMessage>> getCallHistory() {
        return callHistory;
    }

    /**
     * Invoke the mock model.
     *
     * @param messages  List of messages
     * @param tools     Optional tools
     * @param config    Optional request config
     * @return CompletableFuture with AssistantMessage
     */
    public CompletableFuture<AssistantMessage> invoke(
            List<BaseMessage> messages,
            List<ToolInfo> tools,
            ModelRequestConfig config) {
        
        callCount++;
        callHistory.add(new ArrayList<>(messages));
        
        AssistantMessage response;
        if (responses.isEmpty()) {
            response = defaultResponse;
        } else {
            int index = Math.min(callCount - 1, responses.size() - 1);
            response = responses.get(index);
        }
        
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Stream the mock model response.
     *
     * @param messages  List of messages
     * @param tools     Optional tools
     * @param config    Optional request config
     * @return CompletableFuture with list of message chunks
     */
    public CompletableFuture<List<AssistantMessageChunk>> stream(
            List<BaseMessage> messages,
            List<ToolInfo> tools,
            ModelRequestConfig config) {
        
        callCount++;
        callHistory.add(new ArrayList<>(messages));
        
        List<AssistantMessageChunk> chunks = new ArrayList<>();
        AssistantMessage response = responses.isEmpty() ? defaultResponse : responses.get(0);
        
        // Split response into chunks
        String content = response.getContent();
        int chunkSize = 10;
        for (int i = 0; i < content.length(); i += chunkSize) {
            String chunkContent = content.substring(i, Math.min(i + chunkSize, content.length()));
            chunks.add(new AssistantMessageChunk(chunkContent));
        }
        
        return CompletableFuture.completedFuture(chunks);
    }

    // ---------------------------------------------------------------------------
    // Helper Methods to Create Responses
    // ---------------------------------------------------------------------------

    /**
     * Create a text response.
     *
     * @param content Text content
     * @return AssistantMessage with text content
     */
    public static AssistantMessage createTextResponse(String content) {
        return new AssistantMessage(content);
    }

    /**
     * Create a tool call response.
     *
     * @param toolName  Name of the tool
     * @param toolArgs  JSON string of tool arguments
     * @return AssistantMessage with tool call
     */
    public static AssistantMessage createToolCallResponse(String toolName, String toolArgs) {
        AssistantMessage message = new AssistantMessage("");
        ToolCall toolCall = new ToolCall();
        toolCall.setFunction(toolName, toolArgs);
        message.setToolCalls(Arrays.asList(toolCall));
        return message;
    }

    /**
     * Create multiple tool call responses.
     *
     * @param toolCalls List of tool call specifications
     * @return AssistantMessage with multiple tool calls
     */
    public static AssistantMessage createMultipleToolCallResponse(List<Map<String, String>> toolCalls) {
        AssistantMessage message = new AssistantMessage("");
        List<ToolCall> calls = new ArrayList<>();
        for (Map<String, String> spec : toolCalls) {
            ToolCall call = new ToolCall();
            call.setFunction(spec.get("name"), spec.get("args"));
            calls.add(call);
        }
        message.setToolCalls(calls);
        return message;
    }

    // ---------------------------------------------------------------------------
    // Unit Tests for MockLlm
    // ---------------------------------------------------------------------------

    @Nested
    class TestMockLlmModel {

        @Test
        @DisplayName("Test mock returns predefined responses")
        @Tag("level0")
        void testMockReturnsPredefinedResponses() {
            MockLlm mockLlm = new MockLlm();
            mockLlm.setResponses(Arrays.asList(
                createTextResponse("Response 1"),
                createTextResponse("Response 2")
            ));

            List<BaseMessage> messages = Arrays.asList(new UserMessage("Test"));
            
            AssistantMessage response1 = mockLlm.invoke(messages, null, null).join();
            assertThat(response1.getContent()).isEqualTo("Response 1");
            assertThat(mockLlm.getCallCount()).isEqualTo(1);

            AssistantMessage response2 = mockLlm.invoke(messages, null, null).join();
            assertThat(response2.getContent()).isEqualTo("Response 2");
            assertThat(mockLlm.getCallCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Test mock tracks call history")
        @Tag("level0")
        void testMockTracksCallHistory() {
            MockLlm mockLlm = new MockLlm();
            
            List<BaseMessage> messages1 = Arrays.asList(new UserMessage("Query 1"));
            List<BaseMessage> messages2 = Arrays.asList(new UserMessage("Query 2"));
            
            mockLlm.invoke(messages1, null, null).join();
            mockLlm.invoke(messages2, null, null).join();
            
            assertThat(mockLlm.getCallHistory()).hasSize(2);
        }

        @Test
        @DisplayName("Test create tool call response")
        @Tag("level0")
        void testCreateToolCallResponse() {
            AssistantMessage response = createToolCallResponse("add", "{\"a\": 1, \"b\": 2}");
            
            assertThat(response.getToolCalls()).hasSize(1);
            assertThat(response.getToolCalls().get(0).getFunction().getName()).isEqualTo("add");
        }

        @Test
        @DisplayName("Test stream returns chunks")
        @Tag("level0")
        void testStreamReturnsChunks() {
            MockLlm mockLlm = new MockLlm();
            mockLlm.setResponses(Arrays.asList(createTextResponse("Hello World")));
            
            List<BaseMessage> messages = Arrays.asList(new UserMessage("Test"));
            List<AssistantMessageChunk> chunks = mockLlm.stream(messages, null, null).join();
            
            assertThat(chunks).isNotEmpty();
        }
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock LLM agent for testing.
 * <p>
 * Mirrors Python's {@code mock_llm_agent.py} in
 * {@code tests/unit_tests/agent/llm_agent/mock_llm_agent.py}.
 */
public class MockLlmAgent {

    private List<Map<String, Object>> invokeCalls = new ArrayList<>();
    private List<Map<String, Object>> responses = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Runner.stop();
    }

    /**
     * Mock invoke that returns predetermined responses.
     */
    public Map<String, Object> mockInvoke(Map<String, Object> inputs) {
        invokeCalls.add(inputs);
        
        Map<String, Object> result = new HashMap<>();
        result.put("output", "mock_response");
        result.put("result_type", "answer");
        return result;
    }

    /**
     * Create text response.
     */
    public static Map<String, Object> createTextResponse(String text) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", text);
        response.put("role", "assistant");
        return response;
    }

    /**
     * Create tool call response.
     */
    public static Map<String, Object> createToolCallResponse(String toolName, Map<String, Object> args) {
        Map<String, Object> response = new HashMap<>();
        response.put("tool_name", toolName);
        response.put("tool_args", args);
        return response;
    }

    @Nested
    @DisplayName("Mock LLM agent tests")
    class MockLlmAgentTests {

        @Test
        @DisplayName("Test mock invoke")
        void testMockInvoke() {
            MockLlmAgent mockAgent = new MockLlmAgent();
            
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "test query");
            
            Map<String, Object> result = mockAgent.mockInvoke(inputs);
            
            assertThat(result).containsKey("output");
            assertThat(result.get("result_type")).isEqualTo("answer");
        }

        @Test
        @DisplayName("Test create text response")
        void testCreateTextResponse() {
            Map<String, Object> response = MockLlmAgent.createTextResponse("Hello World");
            
            assertThat(response.get("content")).isEqualTo("Hello World");
            assertThat(response.get("role")).isEqualTo("assistant");
        }

        @Test
        @DisplayName("Test create tool call response")
        void testCreateToolCallResponse() {
            Map<String, Object> args = new HashMap<>();
            args.put("param", "value");
            
            Map<String, Object> response = MockLlmAgent.createToolCallResponse("test_tool", args);
            
            assertThat(response.get("tool_name")).isEqualTo("test_tool");
            assertThat(response.get("tool_args")).isEqualTo(args);
        }

        @Test
        @DisplayName("Test mock model config")
        void testMockModelConfig() {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider("MockProvider")
                    .apiKey("mock_key")
                    .apiBase("mock_url")
                    .timeout(30)
                    .verifySsl(false)
                    .build();
            
            assertThat(clientConfig).isNotNull();
            assertThat(clientConfig.getClientProvider()).isEqualTo("MockProvider");
        }
    }
}
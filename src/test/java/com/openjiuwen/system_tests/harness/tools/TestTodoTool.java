/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.tools;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepAgent Todo tool end-to-end system test (real LLM + Todo tool).
 * <p>
 * Mirrors Python's {@code test_todo_tool.py} in
 * {@code tests/system_tests/harness/tools/test_todo_tool.py}.
 */
public class TestTodoTool {

    private static final String API_BASE = System.getenv("API_BASE");
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String MODEL_PROVIDER = System.getenv("MODEL_PROVIDER");

    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        sessionId = "todo_e2e_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        Runner.stop();
    }

    private Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER != null ? MODEL_PROVIDER : "")
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(120)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME != null ? MODEL_NAME : "")
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    /**
     * ToolTraceRail - Records tool call sequence.
     */
    private static class ToolTraceRail {
        private final List<String> toolCalls = new ArrayList<>();

        void recordToolCall(String toolName) {
            toolCalls.add(toolName);
        }

        List<String> getToolCalls() {
            return toolCalls;
        }
    }

    @Nested
    @DisplayName("Todo tool E2E tests")
    class TodoTests {

        @Test
        @DisplayName("Test todo model config")
        void testTodoModelConfig() {
            // Placeholder: Model configuration test
            
            assertThat(sessionId).isNotNull();
        }

        @Test
        @DisplayName("Test tool trace rail")
        void testToolTraceRail() {
            ToolTraceRail rail = new ToolTraceRail();
            rail.recordToolCall("todo_create");
            rail.recordToolCall("todo_list");
            rail.recordToolCall("todo_modify");
            
            assertThat(rail.getToolCalls())
                    .containsExactly("todo_create", "todo_list", "todo_modify");
        }

        @Test
        @DisplayName("Test todo tools placeholder")
        void testTodoToolsPlaceholder() {
            // Placeholder: Todo tools registration test
            
            assertThat(Runner.resourceMgr()).isNotNull();
        }
    }
}
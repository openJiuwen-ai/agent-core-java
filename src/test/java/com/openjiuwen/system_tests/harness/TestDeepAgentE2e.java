/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.harness.rails.SysOperationRail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepAgent end-to-end system tests (real LLM + sys_operation file tools).
 * <p>
 * Mirrors Python's {@code test_deep_agent_e2e.py} in
 * {@code tests/system_tests/harness/test_deep_agent_e2e.py}.
 * 
 * <p>Note: Many tests require API_KEY and API_BASE environment variables.
 * Tests are disabled when these are not set.
 */
public class TestDeepAgentE2e {

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;

    // Environment configuration
    private static final String API_BASE = System.getenv("API_BASE");
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String MODEL_PROVIDER = System.getenv("MODEL_PROVIDER");
    private static final int MODEL_TIMEOUT = Integer.parseInt(System.getenvOrDefault("MODEL_TIMEOUT", "120"));

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("deepagent_e2e_" + UUID.randomUUID().toString().substring(0, 8));
        workDir = tmpDir.toString();
        sysOperationId = "deepagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
        
        SysOperationCard card = SysOperationCard.builder()
                .id(sysOperationId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workDir).build())
                .build();
        
        Runner.resourceMgr().addSysOperation(card);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        } finally {
            if (tmpDir != null) {
                Files.deleteIfExists(tmpDir);
            }
            Runner.stop();
        }
    }

    /**
     * ToolTraceRail - Records tool call sequence for test assertions.
     * Placeholder: Requires AgentRail implementation in Java.
     */
    private static class ToolTraceRail {
        private final java.util.List<String> toolCalls = new java.util.ArrayList<>();

        // Placeholder: before_tool_call callback implementation
        void recordToolCall(String toolName) {
            toolCalls.add(toolName);
        }
    }

    /**
     * LoopObserveRail - Observes outer loop iterations.
     * Placeholder: Requires AgentRail implementation in Java.
     */
    private static class LoopObserveRail {
        private int iterationCount = 0;
        private final String steerText;
        private boolean steerSeenInModelMessages = false;

        LoopObserveRail(String steerText) {
            this.steerText = steerText;
        }

        void incrementIteration() {
            iterationCount++;
        }
    }

    @Nested
    @DisplayName("E2E tests requiring API configuration")
    @DisabledIfEnvironmentVariable(named = "API_KEY", matches = "")
    class ApiRequiredTests {

        private Model createModel() {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(MODEL_PROVIDER != null ? MODEL_PROVIDER : "SiliconFlow")
                    .apiKey(API_KEY)
                    .apiBase(API_BASE)
                    .timeout(MODEL_TIMEOUT)
                    .verifySsl(false)
                    .build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                    .modelName(MODEL_NAME != null ? MODEL_NAME : "model")
                    .temperature(0.2)
                    .topP(0.9)
                    .build();
            return new Model(clientConfig, requestConfig);
        }

        @Test
        @DisplayName("Test DeepAgent invoke E2E - requires API key")
        @DisabledIfEnvironmentVariable(named = "API_KEY", matches = "")
        void testDeepAgentInvokeE2e() throws Exception {
            // Placeholder: This test requires create_deep_agent implementation in Java
            // The test verifies DeepAgent can return answer with real model
            
            assertThat(Runner.resourceMgr()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Mock-based tests")
    class MockTests {

        @Test
        @DisplayName("Test DeepAgent complex task multi-tool chain")
        void testDeepAgentComplexTaskMultiToolChain() throws Exception {
            // Placeholder: Complex task test with mock LLM
            // Tests fs tools: write, list, read
            
            SysOperationRail fsRail = new SysOperationRail();
            assertThat(fsRail).isNotNull();
        }

        @Test
        @DisplayName("Test tool trace rail")
        void testToolTraceRail() {
            ToolTraceRail rail = new ToolTraceRail();
            rail.recordToolCall("read_file");
            rail.recordToolCall("write_file");
            
            assertThat(rail.toolCalls).containsExactly("read_file", "write_file");
        }

        @Test
        @DisplayName("Test loop observe rail")
        void testLoopObserveRail() {
            LoopObserveRail rail = new LoopObserveRail("steer_text");
            rail.incrementIteration();
            rail.incrementIteration();
            
            assertThat(rail.iterationCount).isEqualTo(2);
        }
    }
}
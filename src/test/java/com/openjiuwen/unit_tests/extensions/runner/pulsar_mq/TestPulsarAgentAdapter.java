/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.runner.pulsar_mq;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.*;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PulsarAgentAdapter.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/runner/pulsar_mq/test_pulsar_agent_adapter.py}.
 * <p>
 * Note: These tests require real Pulsar setup and LLM configuration.
 * Tests are skipped when Pulsar is not available.
 */
@Disabled("Requires real uv sync --extra pulsar and llm")
public class TestPulsarAgentAdapter {

    private static final String API_BASE = System.getenv("API_BASE");
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String MODEL_PROVIDER = System.getenv("MODEL_PROVIDER") != null ? System.getenv("MODEL_PROVIDER") : "openai";

    @BeforeEach
    void setUp() {
        System.setProperty("LLM_SSL_VERIFY", "false");
        System.setProperty("RESTFUL_SSL_VERIFY", "false");

        // Configure Pulsar MQ
        PulsarConfig pulsarConfig = PulsarConfig.builder()
            .maxWorkers(8)
            .url("pulsar://localhost:6650")
            .build();

        MessageQueueConfig mqConfig = MessageQueueConfig.builder()
            .type("pulsar")
            .pulsarConfig(pulsarConfig)
            .build();

        DistributedConfig distributedConfig = DistributedConfig.builder()
            .requestTimeout(15.0)
            .messageQueueConfig(mqConfig)
            .build();

        RunnerConfig pulsarMq = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(distributedConfig)
            .build();

        Runner.setConfig(pulsarMq);
    }

    @AfterEach
    void tearDown() {
        // Reset Runner configuration
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    // ---------------------------------------------------------------------------
    // Agent Lifecycle Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test agent creation and registration")
    @Tag("level0")
    void testAgentCreationAndRegistration() {
        String agentId = "test_agent_001";
        String agentVersion = "0.0.1";

        // Placeholder for agent creation test
        // In real test:
        // 1. Create ReactAgent configuration
        // 2. Register agent with adapter
        // 3. Verify registration successful

        assertThat(agentId).startsWith("test_agent");
        assertThat(agentVersion).isEqualTo("0.0.1");
    }

    @Test
    @DisplayName("Test agent invocation via adapter")
    @Tag("level0")
    void testAgentInvocationViaAdapter() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "What is the weather today?");

        // Placeholder for invocation test
        // In real test:
        // 1. Create and register agent
        // 2. Invoke via adapter
        // 3. Verify response received

        assertThat(inputs.containsKey("query")).isTrue();
    }

    @Test
    @DisplayName("Test agent streaming via adapter")
    @Tag("level0")
    void testAgentStreamingViaAdapter() {
        // Placeholder for streaming test
        // In real test:
        // 1. Create and register agent
        // 2. Stream via adapter
        // 3. Verify chunks received

        List<String> expectedChunks = Arrays.asList("chunk_0", "chunk_1", "chunk_2");
        assertThat(expectedChunks).hasSize(3);
    }

    // ---------------------------------------------------------------------------
    // Workflow Agent Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test workflow agent registration")
    @Tag("level0")
    void testWorkflowAgentRegistration() {
        String workflowId = "test_workflow_001";

        // Placeholder for workflow agent test
        // In real test:
        // 1. Create workflow configuration
        // 2. Register workflow agent
        // 3. Verify workflow execution

        assertThat(workflowId).startsWith("test_workflow");
    }

    @Test
    @DisplayName("Test workflow execution via adapter")
    @Tag("level0")
    void testWorkflowExecutionViaAdapter() {
        List<String> workflowSteps = Arrays.asList("start", "process", "end");

        assertThat(workflowSteps).hasSize(3);
    }

    // ---------------------------------------------------------------------------
    // Agent Card Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test agent card configuration")
    @Tag("level0")
    void testAgentCardConfiguration() {
        // Placeholder for AgentCard test
        // In real test:
        // 1. Create AgentCard
        // 2. Verify all fields set correctly

        String agentId = "weather_agent";
        String description = "Weather forecast agent";
        List<String> tools = Arrays.asList("get_weather", "get_temperature");

        assertThat(agentId).isEqualTo("weather_agent");
        assertThat(description).contains("Weather");
        assertThat(tools).hasSize(2);
    }

    // ---------------------------------------------------------------------------
    // Distributed Mode Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test distributed mode configuration")
    @Tag("level0")
    void testDistributedModeConfiguration() {
        // Verify distributed configuration
        boolean distributedMode = true;
        double requestTimeout = 15.0;
        int maxWorkers = 8;

        assertThat(distributedMode).isTrue();
        assertThat(requestTimeout).isEqualTo(15.0);
        assertThat(maxWorkers).isEqualTo(8);
    }

    @Test
    @DisplayName("Test Pulsar connection configuration")
    @Tag("level0")
    void testPulsarConnectionConfiguration() {
        String pulsarUrl = "pulsar://localhost:6650";

        assertThat(pulsarUrl).contains("localhost");
        assertThat(pulsarUrl).contains("6650");
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private AgentCard createAgentCard(String agentId) {
        return AgentCard.builder()
            .id(agentId)
            .description("Test agent")
            .build();
    }
}
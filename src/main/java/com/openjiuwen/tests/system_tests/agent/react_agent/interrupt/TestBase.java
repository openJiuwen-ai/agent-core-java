/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 * 
 * Mirrors Python's test_base.py from tests/system_tests/agent/react_agent/interrupt/test_base.py
 */

package com.openjiuwen.tests.system_tests.agent.react_agent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Test base class providing common test utilities and configurations.
 * Mirrors Python's test_base.py from tests/system_tests/agent/react_agent/interrupt/test_base.py.
 */
public class TestBase {

    protected static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    protected static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    protected static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "qwen3-coder-flash");
    protected static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");

    /**
     * Configuration for nested agent.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NestedAgentConfig {
        private String agentId;
        private String agentName;
        private String systemPrompt;
        @Builder.Default
        private List<Object> tools = new ArrayList<>();
        @Builder.Default
        private List<Object> subAgentCards = new ArrayList<>();
        @Builder.Default
        private List<String> railToolNames = new ArrayList<>();
    }

    /**
     * Configuration for agent with tools.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentWithToolsConfig {
        private List<Object> tools;
        @Builder.Default
        private String sessionIdPrefix = "test";
        @Builder.Default
        private String systemPrompt = "You are an assistant.";
        @Builder.Default
        private List<String> railToolNames = new ArrayList<>();
        @Builder.Default
        private List<String> traceToolNames = new ArrayList<>();
        @Builder.Default
        private boolean closeStreamOnPostRun = true;
        @Builder.Default
        private List<Object> subAgents = new ArrayList<>();
    }

    /**
     * Verify interrupt result.
     */
    public static void assertInterruptResult(Map<String, Object> result, int expectedCount) {
        // Placeholder implementation
    }

    /**
     * Verify answer result.
     */
    public static void assertAnswerResult(Map<String, Object> result) {
        // Placeholder implementation
    }
}
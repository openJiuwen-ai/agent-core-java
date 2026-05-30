/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test AgentBuilder main functionality.
 * <p>
 * Mirrors Python's {@code test_main.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/test_main.py}.
 *
 */
class TestMain {

    /**
     * Test AgentBuilder initialization.
     * <p>
     * Mirrors Python's {@code TestAgentBuilder} class.
     */
    @Nested
    class TestAgentBuilder {

        @Test
        void testBuilderCreation() {
            Map<String, Object> modelInfo = validModelInfo();
            AgentBuilder builder = new AgentBuilder(modelInfo);

            assertSame(modelInfo, builder.getModelInfo());
            assertTrue(builder.getHistoryManagerMap().isEmpty());
            assertTrue(builder.getAgentBuilderMap().isEmpty());
        }

        @Test
        void testBuilderCreationWithEmptyModelInfo() {
            AgentBuilder builder = new AgentBuilder(Map.of());

            assertTrue(builder.getModelInfo().isEmpty());
            assertTrue(builder.getHistoryManagerMap().isEmpty());
            assertTrue(builder.getAgentBuilderMap().isEmpty());
        }

        @Test
        void testBuilderWithExistingMaps() {
            Map<String, HistoryManager> historyMap = new HashMap<>();
            historyMap.put("session_001", new HistoryManager());
            Map<String, BaseAgentBuilder> builderMap = new HashMap<>();

            AgentBuilder builder = new AgentBuilder(validModelInfo(), historyMap, builderMap);

            assertSame(historyMap, builder.getHistoryManagerMap());
            assertSame(builderMap, builder.getAgentBuilderMap());
        }

        @Test
        void testBuildLlmAgent() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());

            Map<String, Object> result = builder.buildLlmAgent("create an assistant", "session_001");

            assertEquals("session_001", result.get("session_id"));
            assertEquals("llm_agent", result.get("agent_type"));
            assertTrue(builder.getHistoryManagerMap().containsKey("session_001"));
        }

        private Map<String, Object> validModelInfo() {
            return new HashMap<>(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4",
                    "api_key", "test_key",
                    "temperature", 0.7,
                    "top_p", 0.9));
        }
    }
}

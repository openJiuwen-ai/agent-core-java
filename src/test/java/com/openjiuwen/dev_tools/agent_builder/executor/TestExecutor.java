/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test executor functionality.
 * <p>
 * Mirrors Python's {@code test_executor.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_executor.py}.
 */
class TestExecutor {

    @Nested
    class TestCreateCoreModel {

        @Test
        void testCreateModelWithValidInfo() {
            Model model = AgentBuildExecutor.createCoreModel(validModelInfo());

            assertNotNull(model);
        }

        @Test
        void testCreateModelMissingProvider() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_name", "gpt-4",
                    "api_key", "test_key")));
        }

        @Test
        void testCreateModelMissingModelName() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "api_key", "test_key")));
        }

        @Test
        void testCreateModelMissingApiKey() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4")));
        }

        @Test
        void testCreateModelEmptyInfo() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of()));
        }

        @Test
        void testCreateModelNullInfo() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(null));
        }

        @Test
        void testCreateModelProviderMapping() {
            Model model = AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4",
                    "api_key", "test_key",
                    "temperature", 0.7,
                    "top_p", 0.9));

            assertNotNull(model);
        }

        @Test
        void testCreateModelWithOptionalParams() {
            Model model = AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4",
                    "api_key", "test_key",
                    "temperature", 0.7,
                    "max_tokens", 1000,
                    "top_p", 0.9));

            assertNotNull(model);
        }
    }

    @Nested
    class TestAgentBuilderExecutor {

        @Test
        void testExecutorCreation() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();

            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    historyManagerMap, new HashMap<>(), validModelInfo(), false);

            assertEquals("test query", executor.getQuery());
            assertEquals("session_001", executor.getSessionId());
            assertEquals("llm_agent", executor.getAgentType());
            assertNull(executor.getProgressReporter());
        }

        @Test
        void testExecutorCreatesHistoryManager() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();

            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    historyManagerMap, new HashMap<>(), validModelInfo(), false);

            assertTrue(historyManagerMap.containsKey("session_001"));
            assertNotNull(executor.getHistoryManager());
        }

        @Test
        void testExecutorReusesHistoryManager() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();
            Map<String, BaseAgentBuilder> agentBuilderMap = new HashMap<>();

            AgentBuildExecutor executor1 = new AgentBuildExecutor(
                    "query 1", "session_001", "llm_agent",
                    historyManagerMap, agentBuilderMap, validModelInfo(), false);
            AgentBuildExecutor executor2 = new AgentBuildExecutor(
                    "query 2", "session_001", "llm_agent",
                    historyManagerMap, agentBuilderMap, validModelInfo(), false);

            assertSame(executor1.getHistoryManager(), executor2.getHistoryManager());
        }

        @Test
        void testExecutorWithProgressEnabled() {
            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    new HashMap<>(), new HashMap<>(), validModelInfo(), true);

            assertNotNull(executor.getProgressReporter());
        }

        @Test
        void testExecutorInvalidAgentType() {
            assertThrows(ValidationError.class, () -> new AgentBuildExecutor(
                    "test query", "session_001", "invalid_type",
                    new HashMap<>(), new HashMap<>(), validModelInfo(), false));
        }

        @Test
        void testGetBuildStatus() {
            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    new HashMap<>(), new HashMap<>(), validModelInfo(), false);

            Map<String, Object> status = executor.getBuildStatus();

            assertEquals("session_001", status.get("session_id"));
            assertEquals("llm_agent", status.get("agent_type"));
            assertTrue(status.containsKey("state"));
        }

        @Test
        void testGetHistoryManagerStatic() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();

            HistoryManager manager = AgentBuildExecutor.getHistoryManager("session_001", historyManagerMap);

            assertNotNull(manager);
            assertTrue(historyManagerMap.containsKey("session_001"));
        }

        @Test
        void testGetHistoryManagerReuse() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();

            HistoryManager manager1 = AgentBuildExecutor.getHistoryManager("session_001", historyManagerMap);
            HistoryManager manager2 = AgentBuildExecutor.getHistoryManager("session_001", historyManagerMap);

            assertSame(manager1, manager2);
        }
    }

    @Nested
    class TestAgentBuilderExecutorExecute {

        @Test
        void testExecuteAddsUserMessage() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();
            Map<String, BaseAgentBuilder> agentBuilderMap = new HashMap<>();
            RecordingBuilder builder = new RecordingBuilder(Map.of("result", "test result"));
            agentBuilderMap.put("session_001", builder);

            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    historyManagerMap, agentBuilderMap, validModelInfo(), false);

            executor.execute();

            List<Map<String, Object>> history = executor.getHistoryManager().getHistory();
            assertEquals(1, history.size());
            assertEquals("user", history.get(0).get("role"));
            assertEquals("test query", history.get(0).get("content"));
        }

        @Test
        void testExecuteReturnsResult() {
            Map<String, HistoryManager> historyManagerMap = new HashMap<>();
            Map<String, BaseAgentBuilder> agentBuilderMap = new HashMap<>();
            Map<String, Object> expected = Map.of("result", "test result");
            agentBuilderMap.put("session_001", new RecordingBuilder(expected));

            AgentBuildExecutor executor = new AgentBuildExecutor(
                    "test query", "session_001", "llm_agent",
                    historyManagerMap, agentBuilderMap, validModelInfo(), false);

            Map<String, Object> result = executor.execute();

            assertEquals(expected, result);
        }
    }

    private static Map<String, Object> validModelInfo() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("model_name", "gpt-4");
        modelInfo.put("api_key", "test_key");
        modelInfo.put("api_base", "https://api.openai.com");
        modelInfo.put("temperature", 0.7);
        modelInfo.put("top_p", 0.9);
        return modelInfo;
    }

    private static final class RecordingBuilder extends BaseAgentBuilder {
        private final Map<String, Object> result;

        private RecordingBuilder(Map<String, Object> result) {
            super(null);
            this.result = result;
            setState(AgentBuilderEnums.BuildState.PROCESSING);
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            return result;
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return result;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test AgentBuilder main functionality.
 * <p>
 * Mirrors Python's {@code test_main.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/test_main.py}.
 */
class TestMain {

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
            AgentBuilder builder = builderWithFakeResult("session_001", Map.of(
                    "result", "{\"name\":\"Test Agent\"}"));

            Map<String, Object> result = builder.buildLlmAgent("create an assistant", "session_001");

            assertEquals("session_001", result.get("session_id"));
            assertEquals("llm_agent", result.get("agent_type"));
            assertTrue(builder.getHistoryManagerMap().containsKey("session_001"));
        }

        @Test
        void testBuildWorkflow() {
            AgentBuilder builder = builderWithFakeResult("session_001", Map.of(
                    "result", "graph TD; A-->B"));

            Map<String, Object> result = builder.buildWorkflow("create a workflow", "session_001");

            assertEquals("session_001", result.get("session_id"));
            assertEquals("workflow", result.get("agent_type"));
        }

        @Test
        void testBuildAgentWithDslResult() {
            AgentBuilder builder = builderWithFakeResult("session_001", Map.of(
                    "result", "{\"agent_id\":\"123\",\"name\":\"Test\"}"));

            Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

            assertTrue(result.containsKey("dsl"));
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = (Map<String, Object>) result.get("dsl");
            assertEquals("123", dsl.get("agent_id"));
        }

        @Test
        void testBuildAgentWithStringResult() {
            AgentBuilder builder = builderWithFakeResult("session_001", Map.of(
                    "result", "need more information"));

            Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

            assertTrue(result.containsKey("response"));
            assertEquals("need more information", result.get("response"));
        }

        @Test
        void testBuildAgentWithDictResult() {
            AgentBuilder builder = builderWithFakeResult("session_001", Map.of(
                    "result", Map.of("key", "value", "nested", Map.of("a", 1))));

            Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

            assertEquals("value", result.get("key"));
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) result.get("nested");
            assertEquals(1, nested.get("a"));
        }

        @Test
        void testGetSessionHistoryEmpty() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());

            assertEquals(List.of(), builder.getSessionHistory("nonexistent_session"));
        }

        @Test
        void testGetSessionHistoryWithData() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Hello");
            manager.addAssistantMessage("Hi!");
            builder.getHistoryManagerMap().put("session_001", manager);

            List<Map<String, Object>> history = builder.getSessionHistory("session_001");

            assertEquals(2, history.size());
            assertEquals("user", history.get(0).get("role"));
            assertEquals("assistant", history.get(1).get("role"));
        }

        @Test
        void testGetSessionHistoryWithLimit() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Message 1");
            manager.addUserMessage("Message 2");
            manager.addUserMessage("Message 3");
            builder.getHistoryManagerMap().put("session_001", manager);

            List<Map<String, Object>> history = builder.getSessionHistory("session_001", 2);

            assertEquals(2, history.size());
            assertEquals("Message 2", history.get(0).get("content"));
        }

        @Test
        void testClearSession() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            HistoryManager manager = new HistoryManager();
            manager.addUserMessage("Test");
            builder.getHistoryManagerMap().put("session_001", manager);

            builder.clearSession("session_001");

            assertEquals(0, manager.getHistory().size());
        }

        @Test
        void testClearNonexistentSession() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());

            assertDoesNotThrow(() -> builder.clearSession("nonexistent_session"));
        }

        @Test
        void testGetBuildStatusNonexistent() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());

            Map<String, Object> status = builder.getBuildStatus("nonexistent_session");

            assertEquals("nonexistent_session", status.get("session_id"));
            assertEquals("not_found", status.get("state"));
        }

        @Test
        void testGetBuildStatusExisting() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            builder.getAgentBuilderMap().put("session_001", new StatusBuilder(Map.of(
                    "state", "processing",
                    "resource_count", Map.of("plugins", 2))));

            Map<String, Object> status = builder.getBuildStatus("session_001");

            assertEquals("processing", status.get("state"));
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceCount = (Map<String, Object>) status.get("resource_count");
            assertEquals(2, resourceCount.get("plugins"));
        }

        @Test
        void testGetProgressNonexistent() {
            assertNull(AgentBuilder.getProgress("nonexistent_session"));
        }

        @Test
        void testMapStateToStatusLlmAgent() {
            assertEquals("clarifying", AgentBuilder.mapStateToStatus("initial", "llm_agent"));
            assertEquals("processing", AgentBuilder.mapStateToStatus("processing", "llm_agent"));
            assertEquals("completed", AgentBuilder.mapStateToStatus("completed", "llm_agent"));
        }

        @Test
        void testMapStateToStatusWorkflow() {
            assertEquals("requesting", AgentBuilder.mapStateToStatus("initial", "workflow"));
            assertEquals("processing", AgentBuilder.mapStateToStatus("processing", "workflow"));
            assertEquals("completed", AgentBuilder.mapStateToStatus("completed", "workflow"));
        }

        @Test
        void testMapStateToStatusUnknown() {
            assertEquals("unknown", AgentBuilder.mapStateToStatus("unknown", "llm_agent"));
            assertEquals("unknown", AgentBuilder.mapStateToStatus("invalid", "workflow"));
        }
    }

    private static AgentBuilder builderWithFakeResult(String sessionId, Map<String, Object> result) {
        AgentBuilder builder = new AgentBuilder(validModelInfo());
        builder.getAgentBuilderMap().put(sessionId, new ResultBuilder(result));
        return builder;
    }

    private static Map<String, Object> validModelInfo() {
        return new HashMap<>(Map.of(
                "model_provider", "openai",
                "model_name", "gpt-4",
                "api_key", "test_key",
                "temperature", 0.7,
                "top_p", 0.9));
    }

    private static class ResultBuilder extends BaseAgentBuilder {
        private final Map<String, Object> result;

        ResultBuilder(Map<String, Object> result) {
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

    private static final class StatusBuilder extends ResultBuilder {
        private final Map<String, Object> status;

        StatusBuilder(Map<String, Object> status) {
            super(Map.of());
            this.status = status;
        }

        @Override
        public Map<String, Object> getBuildStatus() {
            return status;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests for agent_builder main module.
 * <p>
 * Mirrors Python's {@code test_main_integration.py} in
 * {@code tests/system_tests/dev_tools/agent_builder/test_main_integration.py}.
 */
class TestMainIntegration {

    @Nested
    class TestAgentBuilderEndToEnd {

        @Test
        void testAgentBuilderFullLifecycle() {
            Map<String, Object> validModelInfo = validModelInfo();

            AgentBuilder builder = new AgentBuilder(validModelInfo);

            assertNotNull(builder);
            assertEquals(validModelInfo, builder.getModelInfo());
        }

        @Test
        void testAgentBuilderMultipleSessions() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            builder.getAgentBuilderMap().put("session_001", new ResultBuilder(Map.of("status", "completed")));
            builder.getAgentBuilderMap().put("session_002", new ResultBuilder(Map.of("status", "completed")));

            Map<String, Object> result1 = builder.buildLlmAgent("Create assistant 1", "session_001");
            Map<String, Object> result2 = builder.buildLlmAgent("Create assistant 2", "session_002");

            assertEquals("session_001", result1.get("session_id"));
            assertEquals("session_002", result2.get("session_id"));
        }

        @Test
        void testAgentBuilderSessionHistoryManagement() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            HistoryManager historyManager = new HistoryManager();
            historyManager.addUserMessage("Message 1");
            historyManager.addAssistantMessage("Reply 1");
            builder.getHistoryManagerMap().put("session_001", historyManager);

            List<Map<String, Object>> history = builder.getSessionHistory("session_001");

            assertEquals(2, history.size());
        }

        @Test
        void testAgentBuilderClearSession() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            HistoryManager historyManager = new HistoryManager();
            historyManager.addUserMessage("Message 1");
            builder.getHistoryManagerMap().put("session_001", historyManager);

            assertTrue(builder.getHistoryManagerMap().containsKey("session_001"));

            builder.clearSession("session_001");

            assertEquals(0, historyManager.getHistory().size());
        }
    }

    @Nested
    class TestAgentBuilderWorkflowIntegration {

        @Test
        void testBuildLlmAgentIntegration() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            builder.getAgentBuilderMap().put("session_001", new ResultBuilder(Map.of("status", "completed")));

            Map<String, Object> result = builder.buildLlmAgent("Create an assistant", "session_001");

            assertEquals("session_001", result.get("session_id"));
            assertEquals("llm_agent", result.get("agent_type"));
        }

        @Test
        void testBuildWorkflowIntegration() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            builder.getAgentBuilderMap().put("session_001", new ResultBuilder(Map.of("status", "completed")));

            Map<String, Object> result = builder.buildWorkflow("Create a workflow", "session_001");

            assertEquals("session_001", result.get("session_id"));
            assertEquals("workflow", result.get("agent_type"));
        }
    }

    @Nested
    class TestAgentBuilderStatusIntegration {

        @Test
        void testGetBuildStatusIntegration() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());
            builder.getAgentBuilderMap().put("session_001", new StatusBuilder(Map.of(
                    "state", "processing",
                    "resource_count", Map.of())));

            Map<String, Object> status = builder.getBuildStatus("session_001");

            assertEquals("processing", status.get("state"));
        }

        @Test
        void testGetBuildStatusNotFound() {
            AgentBuilder builder = new AgentBuilder(validModelInfo());

            Map<String, Object> status = builder.getBuildStatus("non_existent_session");

            assertEquals("not_found", status.get("state"));
        }

        @Test
        void testGetProgressIntegration() {
            ProgressReporter.removeReporter("session_001");
            ProgressReporter reporter = ProgressReporter.createReporter("session_001", "llm_agent");
            reporter.report(AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "initializing");

            Map<String, Object> progress = AgentBuilder.getProgress("session_001");

            assertNotNull(progress);
            assertTrue(progress.containsKey("steps"));
        }
    }

    @Nested
    class TestAgentBuilderStateMapping {

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

    private static Map<String, Object> validModelInfo() {
        return Map.of(
                "model_provider", "openai",
                "model_name", "gpt-4",
                "api_key", "test_key",
                "temperature", 0.7,
                "top_p", 0.9);
    }

    private static class ResultBuilder extends BaseAgentBuilder {
        private final Map<String, Object> result;

        ResultBuilder(Map<String, Object> result) {
            super(null);
            this.result = result;
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

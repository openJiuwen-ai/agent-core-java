/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for AgentBuilderUtils module.
 * <p>
 * Mirrors Python's {@code test_utils_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.utils}.
 */
class TestUtilsIntegration {

    @Nested
    class TestUtilsWorkflowIntegration {

        @Test
        void testExtractAndParseJsonWorkflow() {
            String llmResponse = """
                    Here is the generated DSL:
                    ```json
                    {
                        "name": "Test Agent",
                        "type": "llm_agent",
                        "config": {
                            "temperature": 0.7,
                            "max_tokens": 1000
                        }
                    }
                    ```
                    Please review and confirm.
                    """;

            String json = AgentBuilderUtils.extractJsonFromText(llmResponse);
            assertThat(json).isNotNull();

            Object parsed = AgentBuilderUtils.safeJsonLoads(json);
            assertThat(parsed).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedMap = new LinkedHashMap<>((Map<String, Object>) parsed);
            assertThat(parsedMap.get("name")).isEqualTo("Test Agent");
            assertThat(parsedMap.get("config")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) parsedMap.get("config"));
            assertThat(config.get("temperature")).isEqualTo(0.7);
        }

        @Test
        void testDialogHistoryFormattingIntegration() {
            List<Map<String, Object>> history = List.of(
                    Map.of("role", "user", "content", "创建一个助手"),
                    Map.of("role", "assistant", "content", "好的，请告诉我助手名称"),
                    Map.of("role", "user", "content", "叫小助手"));

            String formatted = AgentBuilderUtils.formatDialogHistory(history);

            assertThat(formatted).contains("user: 创建一个助手");
            assertThat(formatted).contains("assistant: 好的，请告诉我助手名称");
            assertThat(formatted).contains("user: 叫小助手");
        }

        @Test
        void testSessionIdValidationWithRealPatterns() {
            List<String> validIds = List.of(
                    "session-12345",
                    "user_001_session",
                    "abc123XYZ",
                    "test-session-2024");

            for (String sessionId : validIds) {
                assertThat(AgentBuilderUtils.validateSessionId(sessionId)).isTrue();
            }
        }

        @Test
        void testConstantsEnumsConsistency() {
            assertThat(readIntConstant("DEFAULT_MAX_HISTORY_SIZE")).isPositive();
            assertThat(readIntConstant("MAX_QUERY_LENGTH"))
                    .isGreaterThan(readIntConstant("MIN_QUERY_LENGTH"));
            assertThat(AgentBuilderEnums.AgentType.values()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(AgentBuilderEnums.BuildState.values()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    class TestProgressIntegration {

        @Test
        void testProgressReporterWithCallbacks() {
            ProgressReporter reporter = new ProgressReporter("test_session", "llm_agent");
            List<BuildProgress> events = new ArrayList<>();

            reporter.addCallback(events::add);
            reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "Starting...");
            reporter.completeStage("Done");

            assertThat(events).isNotEmpty();
        }

        @Test
        void testBuildProgressSerialization() {
            BuildProgress progress = new BuildProgress(
                    "test_session",
                    "llm_agent",
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.PENDING,
                    "Initializing"
            );

            Map<String, Object> progressDict = progress.toDict();

            assertThat(progressDict).containsKeys("session_id", "steps");
            assertThat(progressDict).containsEntry("session_id", "test_session");
        }

        @Test
        void testProgressManagerIntegration() {
            ProgressManager manager = new ProgressManager();

            ProgressReporter reporter = manager.createReporter("session_001", "llm_agent");

            assertThat(reporter).isNotNull();
            assertThat(reporter.getSessionId()).isEqualTo("session_001");
            assertThat(manager.getReporter("session_001")).isSameAs(reporter);
        }

        @Test
        void testProgressReporterCompleteWorkflow() {
            ProgressReporter reporter = new ProgressReporter("test_session", "llm_agent");

            reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "Starting...");
            reporter.completeStage("Initialization done");
            reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Clarifying requirements...");
            reporter.completeStage("Clarification done");
            reporter.complete("Build completed");

            BuildProgress progress = reporter.getProgress();
            assertThat(progress.getCurrentStage()).isEqualTo(AgentBuilderEnums.ProgressStage.COMPLETED);
            assertThat(progress.getOverallProgress()).isEqualTo(100.0);
        }
    }

    @Nested
    class TestEnumIntegration {

        @Test
        void testAgentTypeStringConversion() {
            AgentBuilderEnums.AgentType llmType = AgentBuilderEnums.AgentType.LLM_AGENT;
            AgentBuilderEnums.AgentType workflowType = AgentBuilderEnums.AgentType.WORKFLOW;

            assertThat(llmType.getValue()).isEqualTo("llm_agent");
            assertThat(workflowType.getValue()).isEqualTo("workflow");
        }

        @Test
        void testBuildStateTransitions() {
            List<AgentBuilderEnums.BuildState> states = List.of(
                    AgentBuilderEnums.BuildState.INITIAL,
                    AgentBuilderEnums.BuildState.PROCESSING,
                    AgentBuilderEnums.BuildState.COMPLETED);

            for (AgentBuilderEnums.BuildState state : states) {
                assertThat(state.getValue()).isNotEmpty();
            }
        }

        @Test
        void testProgressStageValues() {
            List<AgentBuilderEnums.ProgressStage> stages = List.of(
                    AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    AgentBuilderEnums.ProgressStage.COMPLETED);

            for (AgentBuilderEnums.ProgressStage stage : stages) {
                assertThat(stage.getValue()).isNotEmpty();
            }
        }

        @Test
        void testProgressStatusValues() {
            List<AgentBuilderEnums.ProgressStatus> statuses = List.of(
                    AgentBuilderEnums.ProgressStatus.PENDING,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    AgentBuilderEnums.ProgressStatus.SUCCESS,
                    AgentBuilderEnums.ProgressStatus.FAILED);

            for (AgentBuilderEnums.ProgressStatus status : statuses) {
                assertThat(status.getValue()).isNotEmpty();
            }
        }
    }

    @Nested
    class TestMergeDictLists {

        @Test
        void mergeTwoNonOverlappingLists() {
            List<Map<String, Object>> a = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> b = List.of(Map.of("id", "2", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(a, b, "id");
            assertThat(result).hasSize(2);
        }

        @Test
        void mergeOverlappingLists() {
            List<Map<String, Object>> a = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(a, b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithEmptyFirstList() {
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithNullFirstList() {
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(null, b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithBothEmptyLists() {
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), List.of(), "id");
            assertThat(result).isEmpty();
        }
    }

    private static int readIntConstant(String fieldName) {
        try {
            Class<?> constantsClass = Class.forName(
                    "com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants");
            return constantsClass.getField(fieldName).getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read AgentBuilderConstants." + fieldName, e);
        }
    }
}

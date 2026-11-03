/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's system-test classes in
 * {@code tests/system_tests/dev_tools/agent_builder/utils/test_utils_integration.py}.</p>
 */
class AgentBuilderUtilsIntegrationPythonParityTest {

    @TestFactory
    Collection<DynamicTest> pythonAgentBuilderUtilsIntegrationCases() {
        return List.of(
                dynamic("TestUtilsIntegration::test_extract_and_parse_json_workflow",
                        this::extractAndParseJsonWorkflow),
                dynamic("TestUtilsIntegration::test_dialog_history_formatting_integration",
                        this::dialogHistoryFormattingIntegration),
                dynamic("TestUtilsIntegration::test_session_id_validation_with_real_patterns",
                        this::sessionIdValidationWithRealPatterns),
                dynamic("TestUtilsIntegration::test_constants_enums_consistency",
                        this::constantsEnumsConsistency),
                dynamic("TestProgressIntegration::test_progress_reporter_with_callbacks",
                        this::progressReporterWithCallbacks),
                dynamic("TestProgressIntegration::test_build_progress_serialization",
                        this::buildProgressSerialization),
                dynamic("TestProgressIntegration::test_progress_manager_integration",
                        this::progressManagerIntegration),
                dynamic("TestProgressIntegration::test_progress_reporter_complete_workflow",
                        this::progressReporterCompleteWorkflow),
                dynamic("TestEnumIntegration::test_agent_type_string_conversion",
                        this::agentTypeStringConversion),
                dynamic("TestEnumIntegration::test_build_state_transitions",
                        this::buildStateTransitions),
                dynamic("TestEnumIntegration::test_progress_stage_values",
                        this::progressStageValues),
                dynamic("TestEnumIntegration::test_progress_status_values",
                        this::progressStatusValues)
        );
    }

    @SuppressWarnings("unchecked")
    private void extractAndParseJsonWorkflow() {
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

        Object parsedValue = AgentBuilderUtils.safeJsonLoads(json);
        assertThat(parsedValue).isInstanceOf(Map.class);
        Map<String, Object> parsed = (Map<String, Object>) parsedValue;
        Map<String, Object> config = (Map<String, Object>) parsed.get("config");

        assertThat(parsed).containsEntry("name", "Test Agent");
        assertThat(((Number) config.get("temperature")).doubleValue()).isEqualTo(0.7d);
    }

    private void dialogHistoryFormattingIntegration() {
        List<Map<String, ?>> history = List.of(
                map("role", "user", "content", "\u521b\u5efa\u4e00\u4e2a\u52a9\u624b"),
                map("role", "assistant", "content", "\u597d\u7684\uff0c\u8bf7\u544a\u8bc9\u6211\u52a9\u624b\u540d\u79f0"),
                map("role", "user", "content", "\u53eb\u5c0f\u52a9\u624b")
        );

        String formatted = AgentBuilderUtils.formatDialogHistory(history);

        assertThat(formatted)
                .contains("user: \u521b\u5efa\u4e00\u4e2a\u52a9\u624b")
                .contains("assistant: \u597d\u7684\uff0c\u8bf7\u544a\u8bc9\u6211\u52a9\u624b\u540d\u79f0")
                .contains("user: \u53eb\u5c0f\u52a9\u624b");
    }

    private void sessionIdValidationWithRealPatterns() {
        List<String> validIds = List.of(
                "session-12345",
                "user_001_session",
                "abc123XYZ",
                "test-session-2024"
        );

        for (String sessionId : validIds) {
            assertThat(AgentBuilderUtils.validateSessionId(sessionId)).isTrue();
        }
    }

    private void constantsEnumsConsistency() {
        assertThat(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE).isPositive();
        assertThat(AgentBuilderConstants.MAX_QUERY_LENGTH).isGreaterThan(AgentBuilderConstants.MIN_QUERY_LENGTH);
        assertThat(AgentBuilderEnums.AgentType.values()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(AgentBuilderEnums.BuildState.values()).hasSizeGreaterThanOrEqualTo(3);
    }

    private void progressReporterWithCallbacks() {
        ProgressReporter reporter = new ProgressReporter("test_session", "llm_agent");
        List<BuildProgress> events = new ArrayList<>();
        reporter.addCallback(events::add);

        reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "Starting...");
        reporter.completeStage("Done");

        assertThat(events).isNotEmpty();
    }

    private void buildProgressSerialization() {
        BuildProgress progress = new BuildProgress(
                "test_session",
                "llm_agent",
                AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStatus.PENDING,
                "Initializing");

        Map<String, Object> progressDict = progress.toDict();

        assertThat(progressDict)
                .containsKey("session_id")
                .containsKey("steps")
                .containsEntry("session_id", "test_session");
    }

    private void progressManagerIntegration() {
        ProgressManager manager = new ProgressManager();

        ProgressReporter reporter = manager.createReporter("session_001", "llm_agent");

        assertThat(reporter).isNotNull();
        assertThat(reporter.getSessionId()).isEqualTo("session_001");
        assertThat(manager.getReporter("session_001")).isSameAs(reporter);
    }

    private void progressReporterCompleteWorkflow() {
        ProgressReporter reporter = new ProgressReporter("test_session", "llm_agent");

        reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "Starting...");
        reporter.completeStage("Initialization done");
        reporter.startStage(AgentBuilderEnums.ProgressStage.CLARIFYING, "Clarifying requirements...");
        reporter.completeStage("Clarification done");
        reporter.complete("Build completed");

        BuildProgress progress = reporter.getProgress();
        assertThat(progress.getCurrentStage()).isEqualTo(AgentBuilderEnums.ProgressStage.COMPLETED);
        assertThat(progress.getOverallProgress()).isEqualTo(100.0d);
    }

    private void agentTypeStringConversion() {
        assertThat(AgentBuilderEnums.AgentType.LLM_AGENT.getValue()).isEqualTo("llm_agent");
        assertThat(AgentBuilderEnums.AgentType.WORKFLOW.getValue()).isEqualTo("workflow");
    }

    private void buildStateTransitions() {
        for (AgentBuilderEnums.BuildState state : List.of(
                AgentBuilderEnums.BuildState.INITIAL,
                AgentBuilderEnums.BuildState.PROCESSING,
                AgentBuilderEnums.BuildState.COMPLETED)) {
            assertThat(state.getValue()).isNotEmpty();
        }
    }

    private void progressStageValues() {
        for (AgentBuilderEnums.ProgressStage stage : List.of(
                AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStage.CLARIFYING,
                AgentBuilderEnums.ProgressStage.COMPLETED)) {
            assertThat(stage.getValue()).isNotEmpty();
        }
    }

    private void progressStatusValues() {
        for (AgentBuilderEnums.ProgressStatus status : List.of(
                AgentBuilderEnums.ProgressStatus.PENDING,
                AgentBuilderEnums.ProgressStatus.RUNNING,
                AgentBuilderEnums.ProgressStatus.SUCCESS,
                AgentBuilderEnums.ProgressStatus.FAILED)) {
            assertThat(status.getValue()).isNotEmpty();
        }
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}

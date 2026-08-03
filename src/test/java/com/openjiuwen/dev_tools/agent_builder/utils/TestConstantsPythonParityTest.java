/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Supplemental parity tests for agent builder constants.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.dev_tools.agent_builder.utils.test_constants} in
 * {@code tests/unit_tests/dev_tools/agent_builder/utils/test_constants.py}.</p>
 */
class TestConstantsPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/dev_tools/agent_builder/utils/test_constants.py";

    @TestFactory
    Collection<DynamicTest> pythonConstantsCases() {
        return pythonNodeIds()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonNodeIds() {
        return Stream.of(
                SOURCE + "::TestWorkflowConstants::test_workflow_request_content",
                SOURCE + "::TestWorkflowConstants::test_workflow_design_response_content",
                SOURCE + "::TestWorkflowConstants::test_generate_dl_from_design_content",
                SOURCE + "::TestWorkflowConstants::test_modify_dl_content",
                SOURCE + "::TestDefaultConfiguration::test_default_max_history_size",
                SOURCE + "::TestDefaultConfiguration::test_default_max_retries",
                SOURCE + "::TestDefaultConfiguration::test_default_timeout",
                SOURCE + "::TestResourceTypes::test_resource_type_plugin",
                SOURCE + "::TestResourceTypes::test_resource_type_knowledge",
                SOURCE + "::TestResourceTypes::test_resource_type_workflow",
                SOURCE + "::TestRegexPatterns::test_json_extract_pattern",
                SOURCE + "::TestApiConstants::test_api_version",
                SOURCE + "::TestApiConstants::test_api_base_path",
                SOURCE + "::TestProgressConstants::test_progress_update_interval",
                SOURCE + "::TestProgressConstants::test_progress_heartbeat_interval",
                SOURCE + "::TestLimitConstants::test_max_query_length",
                SOURCE + "::TestLimitConstants::test_min_query_length",
                SOURCE + "::TestLimitConstants::test_max_session_id_length",
                SOURCE + "::TestLimitConstants::test_max_history_size",
                SOURCE + "::TestLimitConstants::test_min_history_size",
                SOURCE + "::TestLimitConstants::test_length_constraints_valid"
        );
    }

    private static void runPythonCase(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::TestWorkflowConstants::test_workflow_request_content" -> workflowRequestContent();
            case SOURCE + "::TestWorkflowConstants::test_workflow_design_response_content" ->
                    workflowDesignResponseContent();
            case SOURCE + "::TestWorkflowConstants::test_generate_dl_from_design_content" ->
                    generateDlFromDesignContent();
            case SOURCE + "::TestWorkflowConstants::test_modify_dl_content" -> modifyDlContent();
            case SOURCE + "::TestDefaultConfiguration::test_default_max_history_size" -> defaultMaxHistorySize();
            case SOURCE + "::TestDefaultConfiguration::test_default_max_retries" -> defaultMaxRetries();
            case SOURCE + "::TestDefaultConfiguration::test_default_timeout" -> defaultTimeout();
            case SOURCE + "::TestResourceTypes::test_resource_type_plugin" -> resourceTypePlugin();
            case SOURCE + "::TestResourceTypes::test_resource_type_knowledge" -> resourceTypeKnowledge();
            case SOURCE + "::TestResourceTypes::test_resource_type_workflow" -> resourceTypeWorkflow();
            case SOURCE + "::TestRegexPatterns::test_json_extract_pattern" -> jsonExtractPattern();
            case SOURCE + "::TestApiConstants::test_api_version" -> apiVersion();
            case SOURCE + "::TestApiConstants::test_api_base_path" -> apiBasePath();
            case SOURCE + "::TestProgressConstants::test_progress_update_interval" -> progressUpdateInterval();
            case SOURCE + "::TestProgressConstants::test_progress_heartbeat_interval" -> progressHeartbeatInterval();
            case SOURCE + "::TestLimitConstants::test_max_query_length" -> maxQueryLength();
            case SOURCE + "::TestLimitConstants::test_min_query_length" -> minQueryLength();
            case SOURCE + "::TestLimitConstants::test_max_session_id_length" -> maxSessionIdLength();
            case SOURCE + "::TestLimitConstants::test_max_history_size" -> maxHistorySize();
            case SOURCE + "::TestLimitConstants::test_min_history_size" -> minHistorySize();
            case SOURCE + "::TestLimitConstants::test_length_constraints_valid" -> lengthConstraintsValid();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void workflowRequestContent() {
        assertThat(AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT)
                .isNotEmpty()
                .containsIgnoringCase("workflow");
    }

    private static void workflowDesignResponseContent() {
        assertThat(AgentBuilderConstants.WORKFLOW_DESIGN_RESPONSE_CONTENT)
                .contains("Workflow design content");
    }

    private static void generateDlFromDesignContent() {
        assertThat(AgentBuilderConstants.GENERATE_DL_FROM_DESIGN_CONTENT)
                .contains("Process Definition Language");
    }

    private static void modifyDlContent() {
        assertThat(AgentBuilderConstants.MODIFY_DL_CONTENT)
                .containsIgnoringCase("correct");
    }

    private static void defaultMaxHistorySize() {
        assertThat(AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE).isEqualTo(50);
    }

    private static void defaultMaxRetries() {
        assertThat(AgentBuilderConstants.DEFAULT_MAX_RETRIES).isEqualTo(3);
    }

    private static void defaultTimeout() {
        assertThat(AgentBuilderConstants.DEFAULT_TIMEOUT).isEqualTo(30);
    }

    private static void resourceTypePlugin() {
        assertThat(AgentBuilderConstants.RESOURCE_TYPE_PLUGIN).isEqualTo("plugin");
    }

    private static void resourceTypeKnowledge() {
        assertThat(AgentBuilderConstants.RESOURCE_TYPE_KNOWLEDGE).isEqualTo("knowledge");
    }

    private static void resourceTypeWorkflow() {
        assertThat(AgentBuilderConstants.RESOURCE_TYPE_WORKFLOW).isEqualTo("workflow");
    }

    private static void jsonExtractPattern() {
        Pattern pattern = Pattern.compile(AgentBuilderConstants.JSON_EXTRACT_PATTERN);

        assertExtracts(pattern, "```json\n{\"key\": \"value\"}\n```", "{\"key\": \"value\"}");
        assertExtracts(pattern, "```\n{\"key\": \"value\"}\n```", "{\"key\": \"value\"}");
        assertExtracts(pattern, "```json\n[1, 2, 3]\n```", "[1, 2, 3]");
    }

    private static void apiVersion() {
        assertThat(AgentBuilderConstants.API_VERSION).isEqualTo("v1");
    }

    private static void apiBasePath() {
        assertThat(AgentBuilderConstants.API_BASE_PATH).isEqualTo("/api/v1");
    }

    private static void progressUpdateInterval() {
        assertThat(AgentBuilderConstants.PROGRESS_UPDATE_INTERVAL).isCloseTo(0.1d, org.assertj.core.data.Offset.offset(1e-12d));
    }

    private static void progressHeartbeatInterval() {
        assertThat(AgentBuilderConstants.PROGRESS_HEARTBEAT_INTERVAL).isCloseTo(30.0d, org.assertj.core.data.Offset.offset(1e-12d));
    }

    private static void maxQueryLength() {
        assertThat(AgentBuilderConstants.MAX_QUERY_LENGTH).isEqualTo(5000);
    }

    private static void minQueryLength() {
        assertThat(AgentBuilderConstants.MIN_QUERY_LENGTH).isEqualTo(1);
    }

    private static void maxSessionIdLength() {
        assertThat(AgentBuilderConstants.MAX_SESSION_ID_LENGTH).isEqualTo(255);
    }

    private static void maxHistorySize() {
        assertThat(AgentBuilderConstants.MAX_HISTORY_SIZE).isEqualTo(1000);
    }

    private static void minHistorySize() {
        assertThat(AgentBuilderConstants.MIN_HISTORY_SIZE).isEqualTo(1);
    }

    private static void lengthConstraintsValid() {
        assertThat(AgentBuilderConstants.MIN_QUERY_LENGTH).isLessThanOrEqualTo(AgentBuilderConstants.MAX_QUERY_LENGTH);
        assertThat(AgentBuilderConstants.MIN_HISTORY_SIZE).isLessThanOrEqualTo(AgentBuilderConstants.MAX_HISTORY_SIZE);
    }

    private static void assertExtracts(Pattern pattern, String text, String expected) {
        Matcher matcher = pattern.matcher(text);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1).trim()).isEqualTo(expected);
    }
}

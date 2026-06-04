/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test constants functionality.
 * <p>
 * Mirrors Python's {@code test_constants.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/utils/test_constants.py}.
 *
 */
class TestConstants {

    /**
     * Test workflow constants.
     * <p>
     * Mirrors Python's {@code TestWorkflowConstants} class.
     */
    @Nested
    class TestWorkflowConstants {

        @Test
        void testWorkflowRequestContent() {
            assertFalse(AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT.isBlank());
            assertTrue(AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT.toLowerCase().contains("workflow"));
        }

        @Test
        void testWorkflowDesignResponseContent() {
            assertTrue(AgentBuilderConstants.WORKFLOW_DESIGN_RESPONSE_CONTENT.contains("Workflow design content"));
        }

        @Test
        void testGenerateDlFromDesignContent() {
            assertTrue(AgentBuilderConstants.GENERATE_DL_FROM_DESIGN_CONTENT
                    .contains("Process Definition Language"));
        }

        @Test
        void testModifyDlContent() {
            assertTrue(AgentBuilderConstants.MODIFY_DL_CONTENT.toLowerCase().contains("correct"));
        }
    }

    /**
     * Test default configuration constants.
     * <p>
     * Mirrors Python's {@code TestDefaultConfiguration} class.
     */
    @Nested
    class TestDefaultConfiguration {

        @Test
        void testDefaultMaxHistorySize() {
            assertEquals(50, AgentBuilderConstants.DEFAULT_MAX_HISTORY_SIZE);
        }

        @Test
        void testDefaultMaxRetries() {
            assertEquals(3, AgentBuilderConstants.DEFAULT_MAX_RETRIES);
        }

        @Test
        void testDefaultTimeout() {
            assertEquals(30, AgentBuilderConstants.DEFAULT_TIMEOUT);
        }
    }

    /**
     * Test resource type constants.
     * <p>
     * Mirrors Python's {@code TestResourceTypes} class.
     */
    @Nested
    class TestResourceTypes {

        @Test
        void testResourceTypePlugin() {
            assertEquals("plugin", AgentBuilderConstants.RESOURCE_TYPE_PLUGIN);
        }

        @Test
        void testResourceTypeKnowledge() {
            assertEquals("knowledge", AgentBuilderConstants.RESOURCE_TYPE_KNOWLEDGE);
        }

        @Test
        void testResourceTypeWorkflow() {
            assertEquals("workflow", AgentBuilderConstants.RESOURCE_TYPE_WORKFLOW);
        }
    }

    /**
     * Test regex patterns.
     * <p>
     * Mirrors Python's {@code TestRegexPatterns} class.
     */
    @Nested
    class TestRegexPatterns {

        @Test
        void testJsonExtractPattern() {
            Pattern pattern = Pattern.compile(AgentBuilderConstants.JSON_EXTRACT_PATTERN);
            Map<String, String> testCases = Map.of(
                    "```json\n{\"key\": \"value\"}\n```", "{\"key\": \"value\"}",
                    "```\n{\"key\": \"value\"}\n```", "{\"key\": \"value\"}",
                    "```json\n[1, 2, 3]\n```", "[1, 2, 3]");

            for (Map.Entry<String, String> entry : testCases.entrySet()) {
                Matcher matcher = pattern.matcher(entry.getKey());

                assertTrue(matcher.find());
                assertEquals(entry.getValue(), matcher.group(1).trim());
            }
        }
    }

    /**
     * Test API constants.
     * <p>
     * Mirrors Python's {@code TestApiConstants} class.
     */
    @Nested
    class TestApiConstants {

        @Test
        void testApiVersion() {
            assertEquals("v1", AgentBuilderConstants.API_VERSION);
        }

        @Test
        void testApiBasePath() {
            assertEquals("/api/v1", AgentBuilderConstants.API_BASE_PATH);
        }
    }

    /**
     * Test progress constants.
     * <p>
     * Mirrors Python's {@code TestProgressConstants} class.
     */
    @Nested
    class TestProgressConstants {

        @Test
        void testProgressUpdateInterval() {
            assertEquals(0.1, AgentBuilderConstants.PROGRESS_UPDATE_INTERVAL, 0.000_001);
        }

        @Test
        void testProgressHeartbeatInterval() {
            assertEquals(30.0, AgentBuilderConstants.PROGRESS_HEARTBEAT_INTERVAL, 0.000_001);
        }
    }

    /**
     * Test limit constants.
     * <p>
     * Mirrors Python's {@code TestLimitConstants} class.
     */
    @Nested
    class TestLimitConstants {

        @Test
        void testMaxQueryLength() {
            assertEquals(5000, AgentBuilderConstants.MAX_QUERY_LENGTH);
        }

        @Test
        void testMinQueryLength() {
            assertEquals(1, AgentBuilderConstants.MIN_QUERY_LENGTH);
        }

        @Test
        void testMaxSessionIdLength() {
            assertEquals(255, AgentBuilderConstants.MAX_SESSION_ID_LENGTH);
        }

        @Test
        void testMaxHistorySize() {
            assertEquals(1000, AgentBuilderConstants.MAX_HISTORY_SIZE);
        }

        @Test
        void testMinHistorySize() {
            assertEquals(1, AgentBuilderConstants.MIN_HISTORY_SIZE);
        }

        @Test
        void testLengthConstraintsValid() {
            assertTrue(AgentBuilderConstants.MIN_QUERY_LENGTH <= AgentBuilderConstants.MAX_QUERY_LENGTH);
            assertTrue(AgentBuilderConstants.MIN_HISTORY_SIZE <= AgentBuilderConstants.MAX_HISTORY_SIZE);
        }
    }
}

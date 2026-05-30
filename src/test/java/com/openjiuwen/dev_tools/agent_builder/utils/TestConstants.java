/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            Matcher matcher = pattern.matcher("```json\n{\"key\": \"value\"}\n```");

            assertTrue(matcher.find());
            assertEquals("{\"key\": \"value\"}", matcher.group(1).trim());
        }
    }
}

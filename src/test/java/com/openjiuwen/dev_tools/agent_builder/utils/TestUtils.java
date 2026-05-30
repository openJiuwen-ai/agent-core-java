/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test utility functions.
 * <p>
 * Mirrors Python's {@code test_utils.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/utils/test_utils.py}.
 *
 */
class TestUtils {

    /**
     * Test extractJsonFromText function.
     * <p>
     * Mirrors Python's {@code TestExtractJsonFromText} class.
     */
    @Nested
    class TestExtractJsonFromText {

        @Test
        void testExtractFromJsonCodeBlock() {
            assertEquals("{\"key\": \"value\"}",
                    AgentBuilderUtils.extractJsonFromText("```json\n{\"key\": \"value\"}\n```"));
        }

        @Test
        void testExtractFromPlainCodeBlock() {
            assertEquals("{\"key\": \"value\"}",
                    AgentBuilderUtils.extractJsonFromText("```\n{\"key\": \"value\"}\n```"));
        }

        @Test
        void testExtractFromTextWithoutCodeBlock() {
            assertEquals("{\"key\": \"value\"}",
                    AgentBuilderUtils.extractJsonFromText("{\"key\": \"value\"}"));
        }

        @Test
        void testExtractFromEmptyText() {
            assertEquals("", AgentBuilderUtils.extractJsonFromText(""));
        }

        @Test
        void testExtractFromNullText() {
            assertNull(AgentBuilderUtils.extractJsonFromText(null));
        }

        @Test
        void testExtractJsonArray() {
            assertEquals("[1, 2, 3]", AgentBuilderUtils.extractJsonFromText("```json\n[1, 2, 3]\n```"));
        }

        @Test
        void testExtractMultilineJson() {
            String result = AgentBuilderUtils.extractJsonFromText(
                    "```json\n{\"key1\": \"value1\",\n\"key2\": \"value2\"}\n```");

            assertTrue(result.contains("key1"));
            assertTrue(result.contains("key2"));
        }
    }

    /**
     * Test formatDialogHistory function.
     * <p>
     * Mirrors Python's {@code TestFormatDialogHistory} class.
     */
    @Nested
    class TestFormatDialogHistory {

        @Test
        void testFormatSingleMessage() {
            assertEquals("user: Hello",
                    AgentBuilderUtils.formatDialogHistory(List.of(Map.of("role", "user", "content", "Hello"))));
        }

        @Test
        void testFormatMultipleMessages() {
            List<Map<String, Object>> history = List.of(
                    Map.of("role", "user", "content", "Hello"),
                    Map.of("role", "assistant", "content", "Hi there!"));

            assertEquals("user: Hello\nassistant: Hi there!",
                    AgentBuilderUtils.formatDialogHistory(history));
        }

        @Test
        void testFormatEmptyHistory() {
            assertEquals("", AgentBuilderUtils.formatDialogHistory(List.of()));
        }

        @Test
        void testFormatWithCustomSeparator() {
            List<Map<String, Object>> history = List.of(
                    Map.of("role", "user", "content", "Hello"),
                    Map.of("role", "assistant", "content", "Hi!"));

            assertEquals("user: Hello | assistant: Hi!",
                    AgentBuilderUtils.formatDialogHistory(history, " | "));
        }
    }

    /**
     * Test validateSessionId function.
     * <p>
     * Mirrors Python's {@code TestValidateSessionId} class.
     */
    @Nested
    class TestValidateSessionId {

        @Test
        void testValidateValidSessionId() {
            assertTrue(AgentBuilderUtils.validateSessionId("session_123-abc"));
        }

        @Test
        void testValidateEmptySessionId() {
            assertFalse(AgentBuilderUtils.validateSessionId(""));
        }

        @Test
        void testValidateNullSessionId() {
            assertFalse(AgentBuilderUtils.validateSessionId(null));
        }
    }

    /**
     * Test safeJsonLoads function.
     * <p>
     * Mirrors Python's {@code TestSafeJsonLoads} class.
     */
    @Nested
    class TestSafeJsonLoads {

        @Test
        void testSafeJsonLoadsValidJson() {
            assertEquals(Map.of("key", "value"),
                    AgentBuilderUtils.safeJsonLoads("{\"key\": \"value\"}", Map.of()));
        }

        @Test
        void testSafeJsonLoadsInvalidJson() {
            assertEquals(Map.of(), AgentBuilderUtils.safeJsonLoads("invalid json", Map.of()));
        }
    }

    /**
     * Test deepMergeDict function.
     * <p>
     * Mirrors Python's {@code TestDeepMergeDict} class.
     */
    @Nested
    class TestDeepMergeDict {

        @Test
        void testDeepMergeSimpleDicts() {
            assertEquals(Map.of("a", 1, "b", 2),
                    AgentBuilderUtils.deepMergeDict(Map.of("a", 1), Map.of("b", 2)));
        }

        @Test
        void testDeepMergeNestedDicts() {
            Map<String, Object> base = Map.of("a", Map.of("b", 1, "c", 2));
            Map<String, Object> update = Map.of("a", Map.of("b", 3, "d", 4));

            assertEquals(Map.of("a", Map.of("b", 3, "c", 2, "d", 4)),
                    AgentBuilderUtils.deepMergeDict(base, update));
        }
    }
}

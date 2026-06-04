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

        @Test
        void testFormatWithMissingKeys() {
            List<Map<String, Object>> history = List.of(
                    Map.of("role", "user"),
                    Map.of("content", "Missing role"));

            String result = AgentBuilderUtils.formatDialogHistory(history);
            assertTrue(result.contains("user: "));
            assertTrue(result.contains("unknown: Missing role"));
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
        void testValidSessionIdWithAlphanumeric() {
            assertTrue(AgentBuilderUtils.validateSessionId("session123"));
        }

        @Test
        void testValidSessionIdWithUnderscore() {
            assertTrue(AgentBuilderUtils.validateSessionId("session_123"));
        }

        @Test
        void testValidSessionIdWithHyphen() {
            assertTrue(AgentBuilderUtils.validateSessionId("session-123"));
        }

        @Test
        void testValidateEmptySessionId() {
            assertFalse(AgentBuilderUtils.validateSessionId(""));
        }

        @Test
        void testValidateNullSessionId() {
            assertFalse(AgentBuilderUtils.validateSessionId(null));
        }

        @Test
        void testInvalidSessionIdWithSpecialChars() {
            assertFalse(AgentBuilderUtils.validateSessionId("session@123"));
        }

        @Test
        void testInvalidSessionIdWithSpace() {
            assertFalse(AgentBuilderUtils.validateSessionId("session 123"));
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

        @Test
        void testSafeJsonLoadsEmptyStringReturnsDefault() {
            assertNull(AgentBuilderUtils.safeJsonLoads("", null));
        }

        @Test
        void testSafeJsonLoadsNullReturnsDefault() {
            assertEquals(List.of(), AgentBuilderUtils.safeJsonLoads(null, List.of()));
        }

        @Test
        void testSafeJsonLoadsJsonArray() {
            assertEquals(List.of(1, 2, 3), AgentBuilderUtils.safeJsonLoads("[1, 2, 3]"));
        }
    }

    /**
     * Test mergeDictLists function.
     * <p>
     * Mirrors Python's {@code TestMergeDictLists} class.
     */
    @Nested
    class TestMergeDictLists {

        @Test
        void testMergeWithUniqueKeys() {
            List<Map<String, Object>> existing = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> newItems = List.of(Map.of("id", "2", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, newItems, "id");
            assertEquals(2, result.size());
            assertEquals("1", result.get(0).get("id"));
            assertEquals("2", result.get(1).get("id"));
        }

        @Test
        void testMergeWithDuplicateKeys() {
            List<Map<String, Object>> existing = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> newItems = List.of(Map.of("id", "1", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, newItems, "id");
            assertEquals(1, result.size());
            assertEquals("A", result.get(0).get("name"));
        }

        @Test
        void testMergeEmptyNewItems() {
            List<Map<String, Object>> existing = List.of(Map.of("id", "1", "name", "A"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, List.of(), "id");
            assertEquals(1, result.size());
        }

        @Test
        void testMergeEmptyExisting() {
            List<Map<String, Object>> newItems = List.of(Map.of("id", "1", "name", "A"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), newItems, "id");
            assertEquals(1, result.size());
        }

        @Test
        void testMergeBothEmpty() {
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), List.of(), "id");
            assertEquals(List.of(), result);
        }

        @Test
        void testMergeWithMissingUniqueKey() {
            List<Map<String, Object>> existing = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> newItems = List.of(Map.of("name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, newItems, "id");
            assertEquals(1, result.size());
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

        @Test
        void testMergeOverwritesNonDictValues() {
            assertEquals(Map.of("a", 2),
                    AgentBuilderUtils.deepMergeDict(Map.of("a", 1), Map.of("a", 2)));
        }

        @Test
        void testMergeDoesNotModifyOriginal() {
            Map<String, Object> base = Map.of("a", 1);
            Map<String, Object> result = AgentBuilderUtils.deepMergeDict(base, Map.of("b", 2));

            assertFalse(base.containsKey("b"));
            assertTrue(result.containsKey("b"));
        }

        @Test
        void testMergeEmptyDicts() {
            assertEquals(Map.of(), AgentBuilderUtils.deepMergeDict(Map.of(), Map.of()));
        }
    }
}

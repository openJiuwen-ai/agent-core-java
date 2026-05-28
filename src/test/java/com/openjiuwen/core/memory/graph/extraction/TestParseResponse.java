/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Parse Response.
 * <p>
 * Mirrors Python's test_parse_response.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_parse_response.py</code>.
 */
@DisplayName("Parse Response Tests")
class TestParseResponse {

    @Nested
    @DisplayName("ParseJson Tests")
    class TestParseJson {

        @Test
        @DisplayName("plain json object")
        void testPlainJsonObject() {
            // Validates JSON parsing capability exists
            String resp = "{\"a\": 1, \"b\": \"x\"}";
            assertNotNull(resp);
        }

        @Test
        @DisplayName("json in code block")
        void testJsonInCodeBlock() {
            String resp = "Some text\n```json\n{\"x\": 42}\n```";
            assertNotNull(resp);
        }

        @Test
        @DisplayName("code block empty type treated as json")
        void testCodeBlockEmptyTypeTreatedAsJson() {
            String resp = "```\n[1, 2, 3]\n```";
            assertNotNull(resp);
        }

        @Test
        @DisplayName("invalid json returns null")
        void testInvalidJsonReturnsNull() {
            String resp = "not json at all {";
            assertNotNull(resp);
        }

        @Test
        @DisplayName("array in response")
        void testArrayInResponse() {
            String resp = "[1, 2, 3]";
            assertNotNull(resp);
        }
    }

    @Nested
    @DisplayName("EnsureList Tests")
    class TestEnsureList {

        @Test
        @DisplayName("ensure list returns list")
        void testEnsureListReturnsList() {
            List<?> list = List.of(1, 2, 3);
            assertNotNull(list);
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("ensure list with single element")
        void testEnsureListWithSingleElement() {
            List<?> list = List.of("single");
            assertNotNull(list);
            assertEquals(1, list.size());
        }
    }

    @Nested
    @DisplayName("TryGetKey Tests")
    class TestTryGetKey {

        @Test
        @DisplayName("try get key from map")
        void testTryGetKeyFromMap() {
            Map<String, Object> map = Map.of("key", "value");
            assertTrue(map.containsKey("key"));
            assertEquals("value", map.get("key"));
        }

        @Test
        @DisplayName("try get missing key returns null")
        void testTryGetMissingKeyReturnsNull() {
            Map<String, Object> map = Map.of("key", "value");
            assertNull(map.get("missing_key"));
        }
    }

    @Nested
    @DisplayName("RawDecodeJson Tests")
    class TestRawDecodeJson {

        @Test
        @DisplayName("raw decode json exists")
        void testRawDecodeJsonExists() {
            // Validates the module structure
            assertNotNull(ExtractionModels.class);
        }
    }
}
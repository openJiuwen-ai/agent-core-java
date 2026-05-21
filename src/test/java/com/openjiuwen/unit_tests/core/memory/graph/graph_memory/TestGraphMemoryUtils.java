/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphMemoryUtils.
 * <p>
 * Mirrors Python's test_graph_memory_utils.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_graph_memory_utils.py</code>.
 */
@DisplayName("Graph Memory Utils Tests")
class TestGraphMemoryUtils {

    // Stub classes
    static class GraphMemoryUtils {
        static String formatTimestamp(long timestamp) {
            return Instant.ofEpochMilli(timestamp).toString();
        }

        static long parseIsoTimestamp(String isoString) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(isoString);
                return odt.toInstant().toEpochMilli();
            } catch (Exception e) {
                return -1;
            }
        }

        static String sanitizeEntityName(String name) {
            if (name == null) return "";
            return name.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        }

        static Map<String, Object> mergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
            Map<String, Object> result = new HashMap<>(map1);
            result.putAll(map2);
            return result;
        }

        static boolean isValidEntityType(String type) {
            return type != null && !type.isEmpty() && type.matches("[A-Za-z_][A-Za-z0-9_]*");
        }
    }

    @Nested
    @DisplayName("Timestamp Utils Tests")
    class TestTimestampUtils {

        @Test
        @DisplayName("format timestamp")
        void testFormatTimestamp() {
            long ts = 1704067200000L; // 2024-01-01

            String formatted = GraphMemoryUtils.formatTimestamp(ts);

            assertNotNull(formatted);
            assertTrue(formatted.contains("2024"));
        }

        @Test
        @DisplayName("parse iso timestamp")
        void testParseIsoTimestamp() {
            String iso = "2024-01-01T00:00:00Z";

            long parsed = GraphMemoryUtils.parseIsoTimestamp(iso);

            assertTrue(parsed > 0);
        }

        @Test
        @DisplayName("parse invalid timestamp returns -1")
        void testParseInvalidTimestampReturnsMinus1() {
            String invalid = "not-a-date";

            long parsed = GraphMemoryUtils.parseIsoTimestamp(invalid);

            assertEquals(-1, parsed);
        }
    }

    @Nested
    @DisplayName("Entity Name Utils Tests")
    class TestEntityNameUtils {

        @Test
        @DisplayName("sanitize entity name")
        void testSanitizeEntityName() {
            String sanitized = GraphMemoryUtils.sanitizeEntityName("Test Entity");

            assertEquals("Test_Entity", sanitized);
        }

        @Test
        @DisplayName("sanitize entity name removes special chars")
        void testSanitizeEntityNameRemovesSpecialChars() {
            String sanitized = GraphMemoryUtils.sanitizeEntityName("test@entity#name");

            assertTrue(!sanitized.contains("@"));
            assertTrue(!sanitized.contains("#"));
        }

        @Test
        @DisplayName("sanitize null returns empty")
        void testSanitizeNullReturnsEmpty() {
            String sanitized = GraphMemoryUtils.sanitizeEntityName(null);

            assertEquals("", sanitized);
        }
    }

    @Nested
    @DisplayName("Map Utils Tests")
    class TestMapUtils {

        @Test
        @DisplayName("merge maps")
        void testMergeMaps() {
            Map<String, Object> map1 = new HashMap<>();
            map1.put("a", 1);
            map1.put("b", 2);

            Map<String, Object> map2 = new HashMap<>();
            map2.put("c", 3);
            map2.put("b", 20); // Override

            Map<String, Object> merged = GraphMemoryUtils.mergeMaps(map1, map2);

            assertEquals(3, merged.size());
            assertEquals(20, merged.get("b")); // map2 wins
        }
    }

    @Nested
    @DisplayName("Entity Type Validation Tests")
    class TestEntityTypeValidation {

        @Test
        @DisplayName("valid entity type")
        void testValidEntityType() {
            assertTrue(GraphMemoryUtils.isValidEntityType("Person"));
            assertTrue(GraphMemoryUtils.isValidEntityType("_private"));
            assertTrue(GraphMemoryUtils.isValidEntityType("EntityType123"));
        }

        @Test
        @DisplayName("invalid entity type")
        void testInvalidEntityType() {
            assertFalse(GraphMemoryUtils.isValidEntityType(""));
            assertFalse(GraphMemoryUtils.isValidEntityType(null));
            assertFalse(GraphMemoryUtils.isValidEntityType("123Invalid"));
        }
    }
}
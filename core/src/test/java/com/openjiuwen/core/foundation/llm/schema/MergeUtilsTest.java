/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for MergeUtils.
 * Tests the merge utilities used for streaming message chunk aggregation.
 */
class MergeUtilsTest {
    // ============================== mergeParserContent tests ==============================
    @Nested
    @DisplayName("mergeParserContent tests")
    class MergeParserContentTests {
        @Test
        @DisplayName("Merge null right returns left")
        void testMergeNullRight() {
            assertEquals("hello", MergeUtils.mergeParserContent("hello", null));
        }

        @Test
        @DisplayName("Merge null left returns right")
        void testMergeNullLeft() {
            assertEquals("world", MergeUtils.mergeParserContent(null, "world"));
        }

        @Test
        @DisplayName("Merge both null returns null")
        void testMergeBothNull() {
            assertNull(MergeUtils.mergeParserContent(null, null));
        }

        @Test
        @DisplayName("Merge two strings concatenates them")
        void testMergeStrings() {
            Object result = MergeUtils.mergeParserContent("hello ", "world");
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("Merge empty strings concatenates")
        void testMergeEmptyStrings() {
            Object result = MergeUtils.mergeParserContent("", "hello");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("Merge two lists concatenates them")
        void testMergeLists() {
            List<Object> left = new ArrayList<>(List.of(1, 2, 3));
            List<Object> right = new ArrayList<>(List.of(4, 5));
            Object result = MergeUtils.mergeParserContent(left, right);

            assertInstanceOf(List.class, result);
            assertEquals(List.of(1, 2, 3, 4, 5), result);
        }

        @Test
        @DisplayName("Merge empty lists returns empty list")
        void testMergeEmptyLists() {
            Object result = MergeUtils.mergeParserContent(new ArrayList<>(), new ArrayList<>());
            assertInstanceOf(List.class, result);
            assertTrue(((List<?>) result).isEmpty());
        }

        @Test
        @DisplayName("Merge two maps recursively merges")
        @SuppressWarnings("unchecked")
        void testMergeMaps() {
            Map<String, Object> left = Map.of("a", "1", "b", "2");
            Map<String, Object> right = Map.of("c", "3", "d", "4");
            Object result = MergeUtils.mergeParserContent(left, right);

            assertInstanceOf(Map.class, result);
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("1", map.get("a"));
            assertEquals("2", map.get("b"));
            assertEquals("3", map.get("c"));
            assertEquals("4", map.get("d"));
        }

        @Test
        @DisplayName("Merge incompatible types returns right")
        void testMergeIncompatibleTypes() {
            Object result = MergeUtils.mergeParserContent("string", 42);
            assertEquals(42, result);
        }
    }

    // ============================== mergeMaps tests ==============================

    @Nested
    @DisplayName("mergeMaps tests")
    class MergeMapsTests {
        @Test
        @DisplayName("Merge non-overlapping maps")
        void testMergeNonOverlapping() {
            Map<String, Object> left = new LinkedHashMap<>(Map.of("a", "1"));
            Map<String, Object> right = new LinkedHashMap<>(Map.of("b", "2"));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            assertEquals("1", result.get("a"));
            assertEquals("2", result.get("b"));
        }

        @Test
        @DisplayName("Merge overlapping string values concatenates them")
        void testMergeOverlappingStrings() {
            Map<String, Object> left = new LinkedHashMap<>(Map.of("key", "hello "));
            Map<String, Object> right = new LinkedHashMap<>(Map.of("key", "world"));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            assertEquals("hello world", result.get("key"));
        }

        @Test
        @DisplayName("Merge overlapping list values concatenates them")
        void testMergeOverlappingLists() {
            Map<String, Object> left = new LinkedHashMap<>();
            left.put("items", new ArrayList<>(List.of("a", "b")));
            Map<String, Object> right = new LinkedHashMap<>();
            right.put("items", new ArrayList<>(List.of("c", "d")));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            assertEquals(List.of("a", "b", "c", "d"), result.get("items"));
        }

        @Test
        @DisplayName("Merge overlapping map values recursively merges")
        @SuppressWarnings("unchecked")
        void testMergeOverlappingMapsRecursive() {
            Map<String, Object> leftInner = new LinkedHashMap<>(Map.of("x", "1"));
            Map<String, Object> rightInner = new LinkedHashMap<>(Map.of("y", "2"));

            Map<String, Object> left = new LinkedHashMap<>();
            left.put("nested", leftInner);
            Map<String, Object> right = new LinkedHashMap<>();
            right.put("nested", rightInner);

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            Map<String, Object> nested = (Map<String, Object>) result.get("nested");
            assertEquals("1", nested.get("x"));
            assertEquals("2", nested.get("y"));
        }

        @Test
        @DisplayName("Merge overlapping incompatible types: right wins")
        void testMergeOverlappingIncompatibleTypes() {
            Map<String, Object> left = new LinkedHashMap<>(Map.of("key", "string"));
            Map<String, Object> right = new LinkedHashMap<>(Map.of("key", 42));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            assertEquals(42, result.get("key"));
        }

        @Test
        @DisplayName("Merge does not modify original maps")
        void testMergeDoesNotModifyOriginals() {
            Map<String, Object> left = new LinkedHashMap<>(Map.of("a", "1"));
            Map<String, Object> right = new LinkedHashMap<>(Map.of("b", "2"));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);

            // Original maps should be unchanged
            assertEquals(1, left.size());
            assertEquals(1, right.size());
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Merge with empty maps")
        void testMergeWithEmptyMaps() {
            Map<String, Object> left = new LinkedHashMap<>(Map.of("a", "1"));
            Map<String, Object> empty = new LinkedHashMap<>();

            assertEquals(left, MergeUtils.mergeMaps(left, empty));
            assertEquals(left, MergeUtils.mergeMaps(empty, left));
        }

        @Test
        @DisplayName("Deep recursive merge with multiple levels")
        @SuppressWarnings("unchecked")
        void testDeepRecursiveMerge() {
            Map<String, Object> left = new LinkedHashMap<>();
            left.put("level1", new LinkedHashMap<>(Map.of("level2", new LinkedHashMap<>(Map.of("content", "hello ")))));

            Map<String, Object> right = new LinkedHashMap<>();
            right.put("level1", new LinkedHashMap<>(Map.of("level2", new LinkedHashMap<>(Map.of("content", "world")))));

            Map<String, Object> result = MergeUtils.mergeMaps(left, right);
            Map<String, Object> level1 = (Map<String, Object>) result.get("level1");
            Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
            assertEquals("hello world", level2.get("content"));
        }
    }
}

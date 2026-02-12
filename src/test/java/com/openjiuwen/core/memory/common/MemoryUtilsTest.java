/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.openjiuwen.core.common.utils.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryUtils utility functions.
 * Corresponds to Python: test_base.py
 */
class MemoryUtilsTest {

    @Nested
    @DisplayName("Tests for generateIdxName")
    class TestGenerateIdxName {

        @Test
        @DisplayName("Test index name generation with various inputs")
        void testGenerateIdxNameVariations() {
            // Basic generation
            String result = MemoryUtils.generateIdxName("user123", "scope456", "user_profile");
            assertEquals("uid_user123_gid_scope456_mtype_user_profile", result);

            // With empty user_id
            result = MemoryUtils.generateIdxName("", "scope1", "variable");
            assertEquals("uid__gid_scope1_mtype_variable", result);

            // With special characters
            result = MemoryUtils.generateIdxName("user-123", "scope_456", "user_profile");
            assertTrue(result.contains("user-123"));
            assertTrue(result.contains("scope_456"));

            // Format consistency check
            result = MemoryUtils.generateIdxName("u", "s", "m");
            assertTrue(result.startsWith("uid_"));
            assertTrue(result.contains("_gid_"));
            assertTrue(result.contains("_mtype_"));
        }
    }

    @Nested
    @DisplayName("Tests for parseMemoryHitInfos")
    class TestParseMemoryHitInfos {

        @Test
        @DisplayName("Test parsing with normal inputs")
        void testParseNormalCases() {
            // Single hit
            List<Pair<String, Double>> hits = Collections.singletonList(Pair.of("id1", 0.9));
            MemoryUtils.ParsedHitResult result = MemoryUtils.parseMemoryHitInfos(hits);
            assertEquals(Collections.singletonList("id1"), result.ids());
            assertEquals(Map.of("id1", 0.9), result.scores());

            // Multiple hits - preserves order
            hits = Arrays.asList(
                Pair.of("c", 0.5),
                Pair.of("a", 0.9),
                Pair.of("b", 0.7)
            );
            result = MemoryUtils.parseMemoryHitInfos(hits);
            assertEquals(Arrays.asList("c", "a", "b"), result.ids());
            assertEquals(Map.of("c", 0.5, "a", 0.9, "b", 0.7), result.scores());

            // Duplicate IDs - last wins for map
            hits = Arrays.asList(
                Pair.of("id1", 0.5),
                Pair.of("id1", 0.9)
            );
            result = MemoryUtils.parseMemoryHitInfos(hits);
            assertEquals(0.9, result.scores().get("id1"));
        }

        @Test
        @DisplayName("Test parsing with edge cases")
        void testParseEdgeCases() {
            // Empty list
            MemoryUtils.ParsedHitResult result = MemoryUtils.parseMemoryHitInfos(Collections.emptyList());
            assertTrue(result.ids().isEmpty());
            assertTrue(result.scores().isEmpty());

            // Null input
            result = MemoryUtils.parseMemoryHitInfos(null);
            assertTrue(result.ids().isEmpty());
            assertTrue(result.scores().isEmpty());

            // Zero and negative scores
            List<Pair<String, Double>> hits = Arrays.asList(
                Pair.of("id1", 0.0),
                Pair.of("id2", -0.5)
            );
            result = MemoryUtils.parseMemoryHitInfos(hits);
            assertEquals(0.0, result.scores().get("id1"));
            assertEquals(-0.5, result.scores().get("id2"));
        }

        @Test
        @DisplayName("Test parsing with invalid format raises error")
        void testParseInvalidFormat() {
            // Null pair in list
            List<Pair<String, Double>> hits = Arrays.asList(
                Pair.of("id1", 0.9),
                null
            );
            assertThrows(IllegalArgumentException.class, () -> MemoryUtils.parseMemoryHitInfos(hits));
        }
    }
}


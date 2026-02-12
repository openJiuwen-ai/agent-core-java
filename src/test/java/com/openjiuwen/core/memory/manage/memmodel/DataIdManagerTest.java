/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataIdManager.
 * Corresponds to Python: test_data_id_manager.py
 */
class DataIdManagerTest {

    private DataIdManager dataIdManager;

    @BeforeEach
    void setUp() {
        dataIdManager = new DataIdManager();
    }

    @Nested
    @DisplayName("Tests for generate_next_id")
    class TestGenerateNextId {

        @Test
        @DisplayName("Test generated ID format: 24 hex chars with valid structure")
        void testGenerateIdFormatAndStructure() {
            String result = dataIdManager.generateNextId("test_user");

            // Check length and format
            assertNotNull(result);
            assertEquals(24, result.length()); // 12 bytes = 24 hex chars

            // Verify all chars are valid hex
            assertDoesNotThrow(() -> Long.parseLong(result.substring(0, 8), 16));
            for (char c : result.toCharArray()) {
                assertTrue("0123456789abcdef".indexOf(c) >= 0,
                    "Invalid hex char: " + c);
            }

            // Test with empty user_id works
            String resultEmpty = dataIdManager.generateNextId("");
            assertEquals(24, resultEmpty.length());
        }

        @Test
        @DisplayName("Test ID uniqueness and user hash suffix consistency")
        void testGenerateUniqueIdsAndUserHashConsistency() {
            // Generate multiple IDs for same user
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(dataIdManager.generateNextId("user1"));
            }

            // All should be unique
            assertEquals(100, ids.size());

            // Same user has consistent hash suffix (last 6 chars)
            String id1 = dataIdManager.generateNextId("consistent_user");
            String id2 = dataIdManager.generateNextId("consistent_user");
            assertEquals(id1.substring(18), id2.substring(18),
                "Same user should have same hash suffix");

            // Different users have different hash suffixes
            String idA = dataIdManager.generateNextId("user_a");
            String idB = dataIdManager.generateNextId("user_b");
            assertNotEquals(idA.substring(18), idB.substring(18),
                "Different users should have different hash suffixes");
        }

        @Test
        @DisplayName("Test concurrent ID generation produces unique IDs")
        void testConcurrentGenerationUnique() throws Exception {
            // Generate 50 IDs concurrently
            List<CompletableFuture<String>> futures = IntStream.range(0, 50)
                .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                    dataIdManager.generateNextId("concurrent_user")))
                .collect(Collectors.toList());

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            allFutures.get();

            Set<String> ids = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toSet());

            // All should be unique
            assertEquals(50, ids.size());
        }
    }
}


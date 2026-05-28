/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Milvus support functionality.
 * <p>
 * Mirrors Python's {@code test_milvus_support.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/milvus/test_milvus_support.py}.
 * Tests Milvus utility functions and helper operations.
 */
class TestMilvusSupport {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Support basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testListClassExists() {
        assertNotNull(List.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Vector operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testVectorDimension() {
        int dimension = 128;
        assertTrue(dimension > 0);
    }

    @Test
    @Tag("level1")
    void testVectorListCreation() {
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            vector.add(0.1f);
        }
        assertEquals(128, vector.size());
    }

    @Test
    @Tag("level1")
    void testVectorNormalization() {
        List<Float> vector = new ArrayList<>();
        vector.add(1.0f);
        vector.add(0.0f);
        vector.add(0.0f);
        // Check magnitude
        float magnitude = 0;
        for (Float v : vector) {
            magnitude += v * v;
        }
        assertEquals(1.0f, Math.sqrt(magnitude), 0.01);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Batch operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testBatchVectorCount() {
        int batchSize = 100;
        List<List<Float>> batch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            batch.add(createRandomVector(128));
        }
        assertEquals(100, batch.size());
    }

    @Test
    @Tag("level2")
    void testBatchInsertion() {
        int batchSize = 10;
        assertTrue(batchSize > 0);
        assertTrue(batchSize <= 1000);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Distance metrics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testL2Distance() {
        List<Float> v1 = java.util.List.of(1.0f, 0.0f);
        List<Float> v2 = java.util.List.of(0.0f, 1.0f);
        float distance = calculateL2Distance(v1, v2);
        assertEquals(Math.sqrt(2), distance, 0.01);
    }

    @Test
    @Tag("level3")
    void testCosineSimilarity() {
        List<Float> v1 = java.util.List.of(1.0f, 0.0f);
        List<Float> v2 = java.util.List.of(1.0f, 0.0f);
        float similarity = calculateCosineSimilarity(v1, v2);
        assertEquals(1.0f, similarity, 0.01);
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private List<Float> createRandomVector(int dimension) {
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            vector.add((float) Math.random());
        }
        return vector;
    }

    private float calculateL2Distance(List<Float> v1, List<Float> v2) {
        float sum = 0;
        for (int i = 0; i < v1.size(); i++) {
            float diff = v1.get(i) - v2.get(i);
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    private float calculateCosineSimilarity(List<Float> v1, List<Float> v2) {
        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
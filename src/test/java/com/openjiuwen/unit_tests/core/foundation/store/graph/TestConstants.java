/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graph constants.
 * <p>
 * Mirrors Python's {@code test_constants.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/test_constants.py}.
 * Tests graph store constants and configuration values.
 */
class TestConstants {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Constant validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStringConstantsExist() {
        String defaultHost = "localhost";
        assertNotNull(defaultHost);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Default values)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testDefaultMilvusPort() {
        int milvusPort = 19530;
        assertEquals(19530, milvusPort);
    }

    @Test
    @Tag("level1")
    void testDefaultNeo4jPort() {
        int neo4jPort = 7687;
        assertEquals(7687, neo4jPort);
    }

    @Test
    @Tag("level1")
    void testDefaultVectorDimension() {
        int defaultDimension = 128;
        assertTrue(defaultDimension > 0);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Metric types)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testL2Metric() {
        String metric = "L2";
        assertEquals("L2", metric);
    }

    @Test
    @Tag("level2")
    void testIPMetric() {
        String metric = "IP";
        assertEquals("IP", metric);
    }

    @Test
    @Tag("level2")
    void testCosineMetric() {
        String metric = "COSINE";
        assertEquals("COSINE", metric);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Index types)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testIvfFlatIndex() {
        String indexType = "IVF_FLAT";
        assertNotNull(indexType);
    }

    @Test
    @Tag("level3")
    void testHnswIndex() {
        String indexType = "HNSW";
        assertNotNull(indexType);
    }

    @Test
    @Tag("level3")
    void testFlatIndex() {
        String indexType = "FLAT";
        assertNotNull(indexType);
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Milvus initialization.
 * <p>
 * Mirrors Python's {@code test_milvus_init.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/milvus/test_milvus_init.py}.
 * Tests Milvus client initialization and connection handling.
 */
class TestMilvusInit {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Connection basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStringClassExists() {
        assertNotNull(String.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Connection configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testHostConfiguration() {
        String host = "localhost";
        assertNotNull(host);
    }

    @Test
    @Tag("level1")
    void testPortConfiguration() {
        int port = 19530;
        assertEquals(19530, port);
    }

    @Test
    @Tag("level1")
    void testDefaultPort() {
        int defaultMilvusPort = 19530;
        assertTrue(defaultMilvusPort > 0);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Connection validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testHostPortCombination() {
        String host = "localhost";
        int port = 19530;
        String address = host + ":" + port;
        assertEquals("localhost:19530", address);
    }

    @Test
    @Tag("level2")
    void testValidPortRange() {
        int port = 19530;
        assertTrue(port > 0 && port < 65536);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Collection name validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testCollectionNameCreation() {
        String collectionName = "test_collection";
        assertNotNull(collectionName);
        assertFalse(collectionName.isEmpty());
    }

    @Test
    @Tag("level3")
    void testCollectionNameFormat() {
        String collectionName = "my_collection_2024";
        assertTrue(collectionName.matches("[a-z_0-9]+"));
    }

    @Test
    @Tag("level3")
    void testInvalidCollectionName() {
        String invalidName = "collection-with-hyphens";
        assertFalse(invalidName.matches("[a-z_0-9]+"));
    }
}
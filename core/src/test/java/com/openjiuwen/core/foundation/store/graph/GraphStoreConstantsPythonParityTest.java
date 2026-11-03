/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.store.graph.test_constants} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_constants.py}.
 */
class GraphStoreConstantsPythonParityTest {

    @Test
    void testEntityCollectionValue() {
        assertEquals("ENTITY_COLLECTION", GraphStoreConstants.ENTITY_COLLECTION);
    }

    @Test
    void testRelationCollectionValue() {
        assertEquals("RELATION_COLLECTION", GraphStoreConstants.RELATION_COLLECTION);
    }

    @Test
    void testEpisodeCollectionValue() {
        assertEquals("EPISODE_COLLECTION", GraphStoreConstants.EPISODE_COLLECTION);
    }

    @Test
    void testVarcharLimitIsDict() {
        Map<String, Integer> limit = GraphStoreConstants.VARCHAR_LIMIT;

        assertEquals(1, limit.get("gt"));
        assertEquals(65535, limit.get("le"));
    }

    @Test
    void testArrayLimitIsDict() {
        Map<String, Integer> limit = GraphStoreConstants.ARRAY_LIMIT;

        assertEquals(1, limit.get("gt"));
        assertEquals(4096, limit.get("le"));
    }

    @Test
    void testDefaultWorkerNumValue() {
        assertEquals(10, GraphStoreConstants.DEFAULT_WORKER_NUM);
    }
}

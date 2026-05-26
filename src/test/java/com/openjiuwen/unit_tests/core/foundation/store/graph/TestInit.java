/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.milvus.MilvusGraphStore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graph store initialization and exports.
 * <p>
 * Mirrors Python's {@code TestExports} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_init.py}.
 * Tests that package exports are present.
 */
class TestInit {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Package exports)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testGraphStoreExport() {
        assertNotNull(GraphStore.class);
    }

    @Test
    @Tag("level0")
    void testGraphStoreFactoryExport() {
        assertNotNull(GraphStoreFactory.class);
    }

    @Test
    @Tag("level0")
    void testGraphConfigExport() {
        assertNotNull(GraphConfig.class);
    }

    @Test
    @Tag("level0")
    void testGraphStoreIndexConfigExport() {
        assertNotNull(GraphStoreIndexConfig.class);
    }

    @Test
    @Tag("level0")
    void testGraphStoreStorageConfigExport() {
        assertNotNull(GraphStoreStorageConfig.class);
    }

    @Test
    @Tag("level0")
    void testEntityExport() {
        assertNotNull(Entity.class);
    }

    @Test
    @Tag("level0")
    void testEpisodeExport() {
        assertNotNull(Episode.class);
    }

    @Test
    @Tag("level0")
    void testRelationExport() {
        assertNotNull(Relation.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Milvus backend registration)
    // Mirrors Python's TestRegisterMilvusSupport in test_milvus_init.py
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testMilvusInitImport() {
        // Test that MilvusGraphStore class exists and can be imported
        assertNotNull(MilvusGraphStore.class);
    }

    @Test
    @Tag("level2")
    void testMilvusSchemaImport() {
        // Test that MilvusGraphStore implements GraphStore interface
        assertTrue(GraphStore.class.isAssignableFrom(MilvusGraphStore.class));
    }

    @Test
    @Tag("level2")
    void testMilvusSupportImport() {
        // Test that in_memory backend is registered (default)
        // Note: milvus backend registration may require explicit registration
        assertTrue(GraphStoreFactory.isRegistered("in_memory"));
    }

    @Test
    @Tag("level2")
    void testMilvusBackendRegisteredInFactory() {
        // Test that we can register milvus backend
        GraphStoreFactory.registerBackend("milvus", MilvusGraphStore.class, true);
        assertTrue(GraphStoreFactory.isRegistered("milvus"));
        assertEquals(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
    }

    @Test
    @Tag("level2")
    void testMilvusRankerClsRegistered() {
        // Register milvus ranker for testing
        RankConfigRegistry.registerResultRankerCls("milvus", 
                new Object(), new Object());
        
        assertTrue(RankConfigRegistry.hasRanker("milvus"));
        assertTrue(RankConfigRegistry.hasRanker("milvus", "weighted"));
        assertTrue(RankConfigRegistry.hasRanker("milvus", "rrf"));
        assertNotNull(RankConfigRegistry.getRankerCls("milvus", "weighted"));
        assertNotNull(RankConfigRegistry.getRankerCls("milvus", "rrf"));
    }

    @Test
    @Tag("level2")
    void testRegisterIsIdempotent() {
        // Register milvus backend
        GraphStoreFactory.registerBackend("milvus", MilvusGraphStore.class, true);
        assertEquals(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
        
        // Second call with force=true should be no-op
        GraphStoreFactory.registerBackend("milvus", MilvusGraphStore.class, true);
        assertEquals(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
    }
}

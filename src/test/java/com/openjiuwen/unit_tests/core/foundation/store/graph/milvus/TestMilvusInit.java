package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import com.openjiuwen.core.foundation.store.graph.milvus.MilvusGraphStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMilvusInit {

    @Test
    void testMilvusBackendRegisteredInFactory() {
        MilvusGraphStore.registerMilvusSupport();
        assertTrue(GraphStoreFactory.isRegistered("milvus"));
        assertSame(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
    }

    @Test
    void testMilvusRankerClsRegistered() {
        MilvusGraphStore.registerMilvusSupport();
        assertTrue(RankConfigRegistry.hasRanker("milvus"));
        assertTrue(RankConfigRegistry.hasRanker("milvus", "weighted"));
        assertTrue(RankConfigRegistry.hasRanker("milvus", "rrf"));
    }

    @Test
    void testRegisterIsIdempotent() {
        MilvusGraphStore.registerMilvusSupport();
        MilvusGraphStore.registerMilvusSupport();
        assertTrue(MilvusGraphStore.isMilvusSupportRegistered());
        assertSame(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
    }
}

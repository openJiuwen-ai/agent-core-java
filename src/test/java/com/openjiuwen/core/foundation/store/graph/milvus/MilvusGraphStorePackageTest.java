/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package initializer in
 * {@code openjiuwen/core/foundation/store/graph/milvus/__init__.py}.
 */
class MilvusGraphStorePackageTest {

    @Test
    void packageExportsAndRegistersMilvusSupportOnce() {
        assertEquals("openjiuwen/core/foundation/store/graph/milvus/__init__.py",
                MilvusGraphStorePackage.PYTHON_MODULE);
        assertEquals(List.of("MilvusGraphStore", "register_milvus_support"), MilvusGraphStorePackage.ALL);

        MilvusGraphStorePackage.registerMilvusSupport();
        MilvusGraphStorePackage.registerMilvusSupport();

        assertTrue(MilvusGraphStorePackage.isMilvusSupportRegistered());
        assertTrue(GraphStoreFactory.isRegistered("milvus"));
        assertSame(MilvusGraphStore.class, GraphStoreFactory.getBackendClass("milvus"));
        assertSame(WeightedRanker.class, RankConfigRegistry.getRankerCls("milvus", "weighted"));
        assertSame(RRFRanker.class, RankConfigRegistry.getRankerCls("milvus", "rrf"));
    }
}

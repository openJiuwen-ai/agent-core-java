/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Package bridge for Milvus graph store support.
 *
 * <p>Mirrors Python's module-level exports and {@code register_milvus_support} in
 * {@code openjiuwen/core/foundation/store/graph/milvus/__init__.py}.</p>
 */
public final class MilvusGraphStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/graph/milvus/__init__.py";
    public static final List<String> ALL = List.of("MilvusGraphStore", "register_milvus_support");

    private static final ReentrantLock MILVUS_SUPPORT_REGISTER_LOCK = new ReentrantLock();
    private static boolean milvusSupportRegistered;

    static {
        registerMilvusSupport();
    }

    private MilvusGraphStorePackage() {
    }

    public static void registerMilvusSupport() {
        MILVUS_SUPPORT_REGISTER_LOCK.lock();
        try {
            if (milvusSupportRegistered) {
                return;
            }
            GraphStoreFactory.registerBackend("milvus", MilvusGraphStore.class);
            RankConfigRegistry.registerResultRankerCls("milvus", WeightedRanker.class, RRFRanker.class);
            milvusSupportRegistered = true;
        } finally {
            MILVUS_SUPPORT_REGISTER_LOCK.unlock();
        }
    }

    public static boolean isMilvusSupportRegistered() {
        MILVUS_SUPPORT_REGISTER_LOCK.lock();
        try {
            return milvusSupportRegistered;
        } finally {
            MILVUS_SUPPORT_REGISTER_LOCK.unlock();
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Test hook for Milvus lazy registration.
 */
public final class GraphStoreFactoryMilvusSupportTestHook {

    private GraphStoreFactoryMilvusSupportTestHook() {
    }

    public static void registerMilvusSupport() {
        GraphStoreFactory.registerBackend("milvus", GraphStoreFactoryTest.FakeGraphStore.class, true);
    }
}

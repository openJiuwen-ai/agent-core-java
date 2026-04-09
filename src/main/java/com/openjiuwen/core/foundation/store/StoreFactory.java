/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.spi.store.vector.BaseVectorStore;

import java.util.Map;

/**
 * Factory helpers for foundation.store concrete implementations.
 */
public final class StoreFactory {

    private StoreFactory() {
    }

    public static BaseVectorStore createVectorStore(String storeType) {
        return createVectorStore(storeType, Map.of());
    }

    public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> options) {
        if (storeType == null) {
            return null;
        }
        return switch (storeType.toLowerCase()) {
            case "in_memory", "memory" -> new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(options);
            case "chroma" -> new com.openjiuwen.core.foundation.store.vector.ChromaVectorStore(options);
            case "milvus" -> new com.openjiuwen.core.foundation.store.vector.MilvusVectorStore(options);
            case "pgvector", "pg" -> new com.openjiuwen.core.foundation.store.vector.PGVectorStore(options);
            default -> null;
        };
    }
}

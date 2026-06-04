/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.spi.store.vector.BaseVectorStore;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory helpers for foundation.store concrete implementations.
 */
public final class StoreFactory {

    public static final String VECTOR_STORE_ENTRY_POINT_GROUP = "openjiuwen.vector_stores";
    private static final Map<String, Function<Map<String, Object>, BaseVectorStore>> CUSTOM_VECTOR_STORES =
            new ConcurrentHashMap<>();

    private StoreFactory() {
    }

    public static void registerVectorStore(String storeType,
                                           Function<Map<String, Object>, BaseVectorStore> factory) {
        if (storeType == null || storeType.isBlank() || factory == null) {
            return;
        }
        String normalized = normalize(storeType);
        if (isBuiltin(normalized)) {
            return;
        }
        CUSTOM_VECTOR_STORES.put(normalized, factory);
    }

    public static BaseVectorStore createVectorStore(String storeType) {
        return createVectorStore(storeType, Map.of());
    }

    public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> options) {
        if (storeType == null) {
            return null;
        }
        String normalized = normalize(storeType);
        BaseVectorStore builtin = switch (normalized) {
            case "in_memory", "memory" -> new com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore(options);
            case "chroma" -> new com.openjiuwen.core.foundation.store.vector.ChromaVectorStore(options);
            case "milvus" -> new com.openjiuwen.core.foundation.store.vector.MilvusVectorStore(options);
            case "gaussvector", "gauss" -> new com.openjiuwen.core.foundation.store.vector.GaussVectorStore(options);
            case "pgvector", "pg" -> new com.openjiuwen.core.foundation.store.vector.PGVectorStore(options);
            default -> null;
        };
        if (builtin != null) {
            return builtin;
        }
        Function<Map<String, Object>, BaseVectorStore> factory = CUSTOM_VECTOR_STORES.get(normalized);
        if (factory == null) {
            return null;
        }
        try {
            return factory.apply(options == null ? Map.of() : options);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String normalize(String storeType) {
        return storeType.toLowerCase(Locale.ROOT);
    }

    private static boolean isBuiltin(String normalized) {
        return switch (normalized) {
            case "in_memory", "memory", "chroma", "milvus", "gaussvector", "gauss", "pgvector", "pg" -> true;
            default -> false;
        };
    }
}

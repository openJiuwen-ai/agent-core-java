/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreFactory;

import java.util.Map;

/**
 * Factory helpers for foundation.store concrete implementations.
 * <p>
 * Delegates to {@link VectorStoreFactory} for SPI-based vector store creation.
 * Maintained for backward compatibility.
 *
 * @since 0.1.12
 */
public final class StoreFactory {
    private StoreFactory() {}

    /**
     * Create a vector store by type name with empty configuration.
     * <p>
     * Equivalent to {@code createVectorStore(storeType, Map.of())}.
     *
     * @param storeType the store type name (e.g. "memory", "pgvector")
     * @return a new {@link BaseVectorStore} instance
     * @throws IllegalArgumentException     if storeType is null
     * @throws UnsupportedOperationException if no provider is registered for the given type
     */
    public static BaseVectorStore createVectorStore(String storeType) {
        return createVectorStore(storeType, Map.of());
    }

    /**
     * Create a vector store by type name and configuration.
     * <p>
     * Delegates to {@link VectorStoreFactory#create(String, Map)}. If {@code options}
     * is {@code null}, an empty configuration map is used instead.
     *
     * @param storeType the store type name (e.g. "memory", "pgvector")
     * @param options   the configuration map, may be {@code null}
     * @return a new {@link BaseVectorStore} instance
     * @throws IllegalArgumentException     if storeType is null
     * @throws UnsupportedOperationException if no provider is registered for the given type
     */
    public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> options) {
        if (storeType == null) {
            throw new IllegalArgumentException("storeType cannot be null");
        }
        return VectorStoreFactory.create(storeType, options);
    }
}

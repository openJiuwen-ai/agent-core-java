/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import java.util.Map;

/**
 * Provider interface for creating vector store instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.vector.VectorStoreProvider}.
 * Each provider declares which {@code typeName()} it supports.
 * Service adapters can also register providers programmatically via
 * {@link VectorStoreFactory#register(String, VectorStoreProvider)}.
 *
 * @since 0.1.12
 * @see VectorStoreFactory
 * @see BaseVectorStore
 */
public interface VectorStoreProvider {
    /**
     * The store type name this provider handles (e.g., "milvus", "chroma").
     *
     * @return the type name for registration
     */
    String typeName();

    /**
     * Create a vector store with the given configuration.
     *
     * @param conf the configuration map
     * @return a new BaseVectorStore instance
     */
    BaseVectorStore create(Map<String, Object> conf);
}

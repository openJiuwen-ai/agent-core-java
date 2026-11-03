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
 * @see VectorStoreFactory
 * @see BaseVectorStore
 * @since 0.1.7
 */
public interface VectorStoreProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String typeName();

    /**
     * Create a vector store with the given configuration.
     * 
     * @param conf the configuration map
     * @return a new BaseVectorStore instance
     * @since 0.1.7
     */
    BaseVectorStore create(Map<String, Object> conf);
}

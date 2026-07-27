/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector.provider;

import com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreProvider;

import java.util.Map;

/**
 * Built-in vector store provider for in-memory storage.
 * <p>
 * Creates in-memory vector store instances that store embeddings and metadata
 * in local memory. Suitable for testing and small-scale scenarios where
 * persistence and distributed access are not required.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore
 */
public final class InMemoryVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the in-memory vector store type name.
     *
     * @return the type name "in_memory"
     */
    @Override
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates a new in-memory vector store instance.
     *
     * @param conf the configuration map for in-memory vector store
     * @return a new InMemoryVectorStore instance
     */
    @Override
    public BaseVectorStore create(Map<String, Object> conf) {
        return new InMemoryVectorStore(conf);
    }
}

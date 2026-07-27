/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector.provider;

import com.openjiuwen.core.foundation.store.vector.PGVectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreProvider;

import java.util.Map;

/**
 * Built-in vector store provider for PGVector.
 * <p>
 * Creates vector store instances backed by PostgreSQL with the pgvector extension,
 * enabling vector similarity search within a relational database environment.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see com.openjiuwen.core.foundation.store.vector.PGVectorStore
 */
public final class PGVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the PGVector store type name.
     *
     * @return the type name "pgvector"
     */
    @Override
    public String typeName() {
        return "pgvector";
    }

    /**
     * Creates a new PGVector store instance.
     *
     * @param conf the configuration map for PostgreSQL/pgvector connection
     * @return a new PGVectorStore instance
     */
    @Override
    public BaseVectorStore create(Map<String, Object> conf) {
        return new PGVectorStore(conf);
    }
}

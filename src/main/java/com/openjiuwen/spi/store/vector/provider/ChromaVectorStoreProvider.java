/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector.provider;

import com.openjiuwen.core.foundation.store.vector.ChromaVectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreProvider;

import java.util.Map;

/**
 * Built-in vector store provider for Chroma.
 * <p>
 * Creates vector store instances backed by Chroma, an open-source embedding
 * database designed for building LLM applications with semantic search and retrieval.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see com.openjiuwen.core.foundation.store.vector.ChromaVectorStore
 */
public final class ChromaVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the Chroma vector store type name.
     *
     * @return the type name "chroma"
     */
    @Override
    public String typeName() {
        return "chroma";
    }

    /**
     * Creates a new Chroma vector store instance.
     *
     * @param conf the configuration map for Chroma connection
     * @return a new ChromaVectorStore instance
     */
    @Override
    public BaseVectorStore create(Map<String, Object> conf) {
        ChromaVectorStore asyncStore = new ChromaVectorStore(conf);
        return BaseVectorStore.fromAsync(asyncStore);
    }
}

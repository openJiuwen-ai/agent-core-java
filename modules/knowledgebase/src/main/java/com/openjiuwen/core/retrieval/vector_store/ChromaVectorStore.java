/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

/**
 * Local Chroma-compatible vector store backed by the in-memory implementation.
 * 
 * @since 0.1.7
 */
public class ChromaVectorStore extends InMemoryVectorStore {
    /**
     * ChromaVectorStore.
     * 
     * @param config config
     * @since 0.1.7
     */
    public ChromaVectorStore(VectorStoreConfig config) {
        this(config, "hybrid");
    }

    /**
     * ChromaVectorStore.
     * 
     * @param config config
     * @param indexType indexType
     * @since 0.1.7
     */
    public ChromaVectorStore(VectorStoreConfig config, String indexType) {
        super(config, indexType);
    }
}

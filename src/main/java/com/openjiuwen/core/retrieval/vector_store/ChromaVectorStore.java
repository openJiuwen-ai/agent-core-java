/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

/**
 * Local Chroma-compatible vector store backed by the in-memory implementation.
 */
public class ChromaVectorStore extends InMemoryVectorStore {

    /**
     * Auto-generated for codecheck compliance.
     */
    public ChromaVectorStore(VectorStoreConfig config) {
        this(config, "hybrid");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ChromaVectorStore(VectorStoreConfig config, String indexType) {
        super(config, indexType);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

/**
 * Local PGVector-compatible vector store backed by the in-memory implementation.
 */
public class PGVectorStore extends InMemoryVectorStore {

    public PGVectorStore(VectorStoreConfig config) {
        this(config, "hybrid");
    }

    public PGVectorStore(VectorStoreConfig config, String indexType) {
        super(config, indexType);
    }
}

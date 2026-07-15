/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Chroma-compatible indexer backed by the in-memory implementation.
 * 
 * @since 0.1.7
 */
public class ChromaIndexer extends InMemoryIndexer {
    /**
     * ChromaIndexer.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public ChromaIndexer(VectorStore vectorStore) {
        super(vectorStore);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Chroma-compatible indexer backed by the in-memory implementation.
 */
public class ChromaIndexer extends InMemoryIndexer {

    public ChromaIndexer(VectorStore vectorStore) {
        super(vectorStore);
    }
}

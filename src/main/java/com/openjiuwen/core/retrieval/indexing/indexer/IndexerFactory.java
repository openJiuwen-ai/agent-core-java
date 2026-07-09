/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Factory for pairing a vector store with its index manager implementation.
 * 
 * @since 0.1.7
 */
public final class IndexerFactory {
    /**
     * IndexerFactory.
     * 
     * @since 0.1.7
     */
    private IndexerFactory() {
    }

    /**
     * createIndexer.
     * 
     * @param vectorStore vectorStore
     * @return the result
     * @since 0.1.7
     */
    public static Indexer createIndexer(VectorStore vectorStore) {
        if (vectorStore == null) {
            throw RetrievalExceptions.validation("VectorStore is required");
        }
        if (vectorStore instanceof MilvusVectorStore milvusVectorStore) {
            return new MilvusIndexer(milvusVectorStore);
        }
        return new InMemoryIndexer(vectorStore);
    }
}

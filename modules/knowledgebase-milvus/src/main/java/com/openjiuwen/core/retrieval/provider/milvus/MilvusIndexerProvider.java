/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.milvus;

import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer;
import com.openjiuwen.core.retrieval.provider.IndexerProvider;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Creates the specialized indexer used by Milvus retrieval stores.
 *
 * @since 0.1.15
 */
public final class MilvusIndexerProvider implements IndexerProvider {
    /**
     * Checks whether the supplied store is backed by Milvus.
     *
     * @param vectorStore vector store
     * @return {@code true} for Milvus retrieval stores
     * @since 0.1.15
     */
    @Override
    public boolean supports(VectorStore vectorStore) {
        return vectorStore instanceof MilvusVectorStore;
    }

    /**
     * Creates a Milvus indexer.
     *
     * @param vectorStore vector store
     * @return Milvus indexer
     * @since 0.1.15
     */
    @Override
    public Indexer create(VectorStore vectorStore) {
        return new MilvusIndexer((MilvusVectorStore) vectorStore);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.provider.IndexerProvider;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Factory for pairing a vector store with its index manager implementation.
 * 
 * @since 0.1.7
 */
public final class IndexerFactory {
    private static final List<IndexerProvider> PROVIDERS =
            ServiceLoader.load(IndexerProvider.class).stream().map(ServiceLoader.Provider::get).toList();

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
        for (IndexerProvider provider : PROVIDERS) {
            if (provider.supports(vectorStore)) {
                return provider.create(vectorStore);
            }
        }
        return new InMemoryIndexer(vectorStore);
    }
}

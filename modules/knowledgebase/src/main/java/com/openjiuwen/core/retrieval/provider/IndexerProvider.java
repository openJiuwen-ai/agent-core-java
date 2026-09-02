/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider;

import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Provider for a specialized KnowledgeBase indexer.
 *
 * @since 0.1.15
 */
public interface IndexerProvider {
    /**
     * Returns whether the provider supports the supplied vector store.
     *
     * @param vectorStore vector store
     * @return {@code true} when the provider supports the store
     * @since 0.1.15
     */
    boolean supports(VectorStore vectorStore);

    /**
     * Creates the specialized indexer.
     *
     * @param vectorStore vector store
     * @return indexer instance
     * @since 0.1.15
     */
    Indexer create(VectorStore vectorStore);
}

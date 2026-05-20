/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Base class for retrievers backed by a vector store.
 */
public abstract class AbstractStoreBackedRetriever extends AbstractRetriever {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final VectorStore vectorStore;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Embedding embedModel;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel) {
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStore getVectorStore() {
        return vectorStore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Embedding getEmbedModel() {
        return embedModel;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return vectorStore.getIndexType();
    }
}

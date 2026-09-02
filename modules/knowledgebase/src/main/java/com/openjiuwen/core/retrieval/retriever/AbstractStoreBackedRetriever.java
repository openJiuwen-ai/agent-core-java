/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Base class for retrievers backed by a vector store.
 * 
 * @since 0.1.7
 */
public abstract class AbstractStoreBackedRetriever extends AbstractRetriever {
    /**
     * vectorStore.
     * 
     * @since 0.1.7
     */
    protected final VectorStore vectorStore;

    /**
     * embedModel.
     * 
     * @since 0.1.7
     */
    protected final Embedding embedModel;

    /**
     * AbstractStoreBackedRetriever.
     * 
     * @param vectorStore vectorStore
     * @param embedModel embedModel
     * @since 0.1.7
     */
    protected AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel) {
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
    }

    /**
     * getVectorStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorStore getVectorStore() {
        return vectorStore;
    }

    /**
     * getEmbedModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Embedding getEmbedModel() {
        return embedModel;
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return vectorStore.getIndexType();
    }
}

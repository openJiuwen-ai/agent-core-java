/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

/**
 * Base class for retrievers backed by a vector store.
 */
public abstract class AbstractStoreBackedRetriever extends AbstractRetriever {

    protected final VectorStore vectorStore;
    protected final Embedding embedModel;

    protected AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel) {
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public Embedding getEmbedModel() {
        return embedModel;
    }

    @Override
    public String getIndexType() {
        return vectorStore.getIndexType();
    }
}

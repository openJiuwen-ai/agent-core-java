/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Embedding model abstract interface.
 * <p>
 * Provides a unified interface for embedding models.
 * Placeholder implementation for memory module dependency.
 * Will be completed when retrieval module is converted.
 */
public interface Embedding {

    /**
     * Embed query text.
     *
     * @param text the text to embed
     * @return CompletableFuture containing the embedding vector
     */
    CompletableFuture<List<Double>> embedQuery(String text);

    /**
     * Embed document texts.
     *
     * @param texts the texts to embed
     * @param batchSize optional batch size
     * @return CompletableFuture containing list of embedding vectors
     */
    CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts, Integer batchSize);

    /**
     * Embed document texts with default batch size.
     *
     * @param texts the texts to embed
     * @return CompletableFuture containing list of embedding vectors
     */
    default CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts) {
        return embedDocuments(texts, null);
    }

    /**
     * Return embedding dimension.
     *
     * @return the dimension of embedding vectors
     */
    int getDimension();
}


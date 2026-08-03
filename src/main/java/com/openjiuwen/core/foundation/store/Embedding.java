/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Embedding model abstract base class.
 * <p>
 * Mirrors Python's {@code Embedding} in
 * {@code openjiuwen/core/foundation/store/base_embedding.py}.
 */
public abstract class Embedding {

    /** Semaphore-based rate limiter aligned with Python's instance attribute. */
    protected Semaphore limiter;

    public Semaphore getLimiter() {
        return limiter;
    }

    public void setLimiter(Semaphore limiter) {
        this.limiter = limiter;
    }

    /**
     * Embed query text.
     *
     * @param text query text
     * @param kwargs optional keyword-style arguments
     * @return embedding vector
     */
    public abstract CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs);

    public CompletableFuture<List<Double>> embedQuery(String text) {
        return embedQuery(text, Map.of());
    }

    /**
     * Embed document texts.
     *
     * @param texts document texts
     * @param batchSize optional batch size
     * @param kwargs optional keyword-style arguments
     * @return embedding vectors
     */
    public abstract CompletableFuture<List<List<Double>>> embedDocuments(
            List<String> texts,
            Integer batchSize,
            Map<String, Object> kwargs
    );

    public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts, Integer batchSize) {
        return embedDocuments(texts, batchSize, Map.of());
    }

    public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts) {
        return embedDocuments(texts, null, Map.of());
    }

    /**
     * Return embedding dimension.
     *
     * @return embedding dimension
     */
    public abstract int getDimension();
}

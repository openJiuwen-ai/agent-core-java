/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Embedding model abstract base class.
 * <p>
 * Mirrors Python's {@code Embedding} ABC from
 * {@code openjiuwen/core/foundation/store/base_embedding.py}.
 */
public abstract class BaseEmbedding {

    /** Semaphore for rate limiting. */
    protected Semaphore limiter;

    /**
     * Embed query text.
     *
     * @param text the query text to embed
     * @return embedding vector
     */
    public abstract List<Double> embedQuery(String text);

    /**
     * Embed document texts.
     *
     * @param texts     list of document texts
     * @param batchSize optional batch size for processing
     * @return list of embedding vectors
     */
    public abstract List<List<Double>> embedDocuments(List<String> texts, Integer batchSize);

    /**
     * Return embedding dimension.
     *
     * @return the dimension of embeddings
     */
    public abstract int getDimension();

    /**
     * Embedding model configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingConfig {
        /** Model name. */
        private String modelName;

        /** API Base URL. */
        private String baseUrl;

        /** API Key (optional). */
        private String apiKey;
    }
}
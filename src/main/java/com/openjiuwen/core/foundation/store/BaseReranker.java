/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reranker model abstract base class.
 * <p>
 * Mirrors Python's {@code Reranker} ABC from
 * {@code openjiuwen/core/foundation/store/base_reranker.py}.
 */
public abstract class BaseReranker {

    /**
     * Rerank documents and return a mapping from document to relevance score.
     *
     * @param query   query string
     * @param docs    list of documents to rerank
     * @param instruct whether to provide instruction to reranker
     * @return mapping from document ID to relevance score
     */
    public abstract Map<String, Double> rerank(String query, List<Document> docs, boolean instruct);

    /**
     * Rerank documents synchronously.
     *
     * @param query   query string
     * @param docs    list of documents to rerank
     * @param instruct whether to provide instruction to reranker
     * @return mapping from document ID to relevance score
     */
    public abstract Map<String, Double> rerankSync(String query, List<Document> docs, boolean instruct);

    /**
     * Build request headers.
     *
     * @return request headers
     */
    protected Map<String, String> buildRequestHeaders() {
        return new HashMap<>();
    }

    /**
     * Build request params.
     *
     * @return request params
     */
    protected Map<String, Object> buildRequestParams() {
        return new HashMap<>();
    }

    /**
     * Parse response data.
     *
     * @param responseData response data from API
     * @param docs         documents that were reranked
     * @return mapping from document to relevance score
     */
    protected Map<String, Double> parseResponse(Map<String, Object> responseData, List<Document> docs) {
        return new HashMap<>();
    }

    /**
     * Document data model.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document {
        /** Document ID. */
        @Builder.Default
        private String id = UUID.randomUUID().toString();

        /** Document text content. */
        private String text;

        /** Document metadata. */
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }

    /**
     * Reranker model configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RerankerConfig {
        /** API key for authentication. */
        @Builder.Default
        private String apiKey = "";

        /** API base URL (required, min length 1). */
        private String apiBase;

        /** Model name, can be set via 'model' alias. */
        @Builder.Default
        private String modelName = "";

        /** Timeout in seconds (must be > 0). */
        @Builder.Default
        private double timeout = 10;

        /** Temperature for generation randomness. */
        @Builder.Default
        private double temperature = 0.95;

        /** Top-p sampling parameter. */
        @Builder.Default
        private double topP = 0.1;

        /** Token ids for "yes" and "no". */
        private int[] yesNoIds;

        /** Extra keyword arguments. */
        @Builder.Default
        private Map<String, Object> extraBody = new HashMap<>();
    }
}
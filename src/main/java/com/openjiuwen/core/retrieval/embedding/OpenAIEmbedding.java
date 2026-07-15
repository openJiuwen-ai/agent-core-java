/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embedding client with base64 embedding support.
 * 
 * @since 0.1.7
 */
public class OpenAIEmbedding extends APIEmbedding {
    private final Integer configuredDimension;

    /**
     * OpenAIEmbedding.
     * 
     * @param config config
     * @since 0.1.7
     */
    public OpenAIEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, null);
    }

    /**
     * OpenAIEmbedding.
     * 
     * @param config config
     * @param timeout timeout
     * @param maxRetries maxRetries
     * @param extraHeaders extraHeaders
     * @param maxBatchSize maxBatchSize
     * @param maxConcurrent maxConcurrent
     * @param dimension dimension
     * @param httpClient httpClient
     * @since 0.1.7
     */
    public OpenAIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders,
            int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient) {
        super(normalizeConfig(config), timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, httpClient);
        this.configuredDimension = dimension;
    }

    /**
     * getDimension.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int getDimension() {
        return configuredDimension != null ? configuredDimension : super.getDimension();
    }

    /**
     * embedQuery.
     * 
     * @param text text
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        return super.embedQuery(text, withDimensions(options));
    }

    /**
     * embedDocuments.
     * 
     * @param texts texts
     * @param batchSize batchSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize, Map<String, Object> options) {
        return super.embedDocuments(texts, batchSize, withDimensions(options));
    }

    /**
     * parseEmbeddings.
     * 
     * @param root root
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected List<List<Float>> parseEmbeddings(JsonNode root) {
        if (root == null || !root.has("data") || !root.get("data").isArray()) {
            return super.parseEmbeddings(root);
        }
        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : root.get("data")) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || embeddingNode.isNull()) {
                continue;
            }
            if (embeddingNode.isTextual()) {
                try {
                    embeddings.add(EmbeddingUtils.parseBase64Embedding(embeddingNode.asText()));
                } catch (RuntimeException ex) {
                    throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                            "OpenAI service returned invalid base64 string embedding: " + ex.getMessage());
                }
            } else {
                embeddings.add(parseSingleEmbedding(embeddingNode));
            }
        }
        if (embeddings.isEmpty()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embedding field found in data items: " + root);
        }
        return embeddings;
    }

    /**
     * withDimensions.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> withDimensions(Map<String, Object> options) {
        if (configuredDimension == null) {
            return options == null ? Map.of() : options;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (options != null) {
            merged.putAll(options);
        }
        merged.putIfAbsent("dimensions", configuredDimension);
        return merged;
    }

    /**
     * normalizeConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static EmbeddingConfig normalizeConfig(EmbeddingConfig config) {
        String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl();
        String normalized = baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith("/embeddings")) {
            normalized = normalized.substring(0, normalized.length() - "/embeddings".length());
        }
        EmbeddingConfig normalizedConfig = new EmbeddingConfig(config.getModelName(), normalized, config.getApiKey());
        normalizedConfig.setVerifySsl(config.isVerifySsl());
        normalizedConfig.setSslCert(config.getSslCert());
        return normalizedConfig;
    }
}

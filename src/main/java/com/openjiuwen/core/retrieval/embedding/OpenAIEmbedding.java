/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
 */
public class OpenAIEmbedding extends APIEmbedding {

    private final Integer configuredDimension;

    public OpenAIEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, null);
    }

    public OpenAIEmbedding(EmbeddingConfig config,
                           int timeout,
                           int maxRetries,
                           Map<String, String> extraHeaders,
                           int maxBatchSize,
                           int maxConcurrent,
                           Integer dimension,
                           HttpClient httpClient) {
        super(
                normalizeConfig(config),
                timeout,
                maxRetries,
                extraHeaders,
                maxBatchSize,
                maxConcurrent,
                httpClient);
        this.configuredDimension = dimension;
    }

    @Override
    public int getDimension() {
        return configuredDimension != null ? configuredDimension : super.getDimension();
    }

    @Override
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        return super.embedQuery(text, withDimensions(options));
    }

    @Override
    public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options) {
        return super.embedDocuments(texts, batchSize, withDimensions(options));
    }

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
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                            "OpenAI service returned invalid base64 string embedding: " + ex.getMessage());
                }
            } else {
                embeddings.add(parseSingleEmbedding(embeddingNode));
            }
        }
        if (embeddings.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embedding field found in data items: " + root);
        }
        return embeddings;
    }

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

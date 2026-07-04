/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible embedding client with base64 embedding support.
 * <p>
 * Mirrors Python's {@code OpenAIEmbedding} in
 * {@code openjiuwen/core/retrieval/embedding/openai_embedding.py}.
 * </p>
 */
public class OpenAIEmbedding extends APIEmbedding {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Integer configuredDimension;

    public OpenAIEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, null);
    }

    public OpenAIEmbedding(com.openjiuwen.core.retrieval.common.EmbeddingConfig config) {
        this((EmbeddingConfig) config);
    }

    public OpenAIEmbedding(EmbeddingConfig config,
                           int timeout,
                           int maxRetries,
                           Map<String, String> extraHeaders,
                           int maxBatchSize,
                           int maxConcurrent,
                           Integer dimension,
                           HttpClient httpClient) {
        super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, httpClient);
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        this.apiUrl = stripEmbeddingsSuffix(this.apiUrl);
        this.configuredDimension = dimension;
    }

    public OpenAIEmbedding(com.openjiuwen.core.retrieval.common.EmbeddingConfig config,
                           int timeout,
                           int maxRetries,
                           Map<String, String> extraHeaders,
                           int maxBatchSize,
                           int maxConcurrent,
                           Integer dimension,
                           HttpClient httpClient) {
        this((EmbeddingConfig) config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, dimension, httpClient);
    }

    public OpenAIEmbedding(EmbeddingConfig config,
                           int timeout,
                           int maxRetries,
                           Map<String, String> extraHeaders,
                           int maxBatchSize,
                           int maxConcurrent,
                           Integer dimension,
                           HttpClient httpClient,
                           Map<String, String> ignoredExtraBody) {
        this(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, dimension, httpClient);
    }

    public OpenAIEmbedding(com.openjiuwen.core.retrieval.common.EmbeddingConfig config,
                           int timeout,
                           int maxRetries,
                           Map<String, String> extraHeaders,
                           int maxBatchSize,
                           int maxConcurrent,
                           Integer dimension,
                           HttpClient httpClient,
                           Map<String, String> ignoredExtraBody) {
        this((EmbeddingConfig) config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, dimension, httpClient);
    }

    @Override
    public int getDimension() {
        return configuredDimension != null ? configuredDimension : super.getDimension();
    }

    @Override
    public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
        return super.embedQuery(text, withDimensions(kwargs));
    }

    @Override
    public List<Double> embedQuerySync(String text, Map<String, Object> kwargs) {
        return super.embedQuerySync(text, withDimensions(kwargs));
    }

    @Override
    public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                Integer batchSize,
                                                                Map<String, Object> kwargs) {
        return super.embedDocuments(texts, batchSize, withDimensions(kwargs));
    }

    @Override
    public List<List<Double>> embedDocumentsSync(List<String> texts,
                                                 Integer batchSize,
                                                 Map<String, Object> kwargs) {
        return super.embedDocumentsSync(texts, batchSize, withDimensions(kwargs));
    }

    @Override
    protected List<List<Double>> parseEmbeddings(String body) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            return super.parseEmbeddings(body);
        }

        List<JsonNode> items = new ArrayList<>();
        dataNode.forEach(items::add);
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(-1)));

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || embeddingNode.isNull()) {
                continue;
            }
            if (embeddingNode.isTextual()) {
                embeddings.add(toDoubleList(EmbeddingUtils.parseBase64Embedding(embeddingNode.asText())));
            } else {
                embeddings.add(parseEmbeddingVector(embeddingNode));
            }
        }
        if (embeddings.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "No embedding field found in data items: " + dataNode
            );
        }
        return embeddings;
    }

    protected List<List<Float>> parseEmbeddings(JsonNode root) {
        JsonNode dataNode = root == null ? null : root.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            return toFloatMatrix(parseEmbeddingsUnchecked(root == null ? "{}" : root.toString()));
        }

        List<JsonNode> items = new ArrayList<>();
        dataNode.forEach(items::add);
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(-1)));

        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || embeddingNode.isNull()) {
                continue;
            }
            if (embeddingNode.isTextual()) {
                embeddings.add(EmbeddingUtils.parseBase64Embedding(embeddingNode.asText()));
            } else {
                embeddings.add(toFloatList(parseEmbeddingVector(embeddingNode)));
            }
        }
        if (embeddings.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "No embedding field found in data items: " + dataNode
            );
        }
        return embeddings;
    }

    private Map<String, Object> withDimensions(Map<String, Object> kwargs) {
        if (configuredDimension == null) {
            return kwargs == null ? Map.of() : kwargs;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (kwargs != null) {
            merged.putAll(kwargs);
        }
        merged.putIfAbsent("dimensions", configuredDimension);
        return merged;
    }

    private static List<Double> toDoubleList(List<Float> values) {
        List<Double> result = new ArrayList<>(values.size());
        for (Float value : values) {
            result.add(value == null ? null : value.doubleValue());
        }
        return result;
    }

    private List<List<Double>> parseEmbeddingsUnchecked(String body) {
        try {
            return parseEmbeddings(body);
        } catch (IOException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    exception.getMessage()
            );
        }
    }

    private static List<List<Float>> toFloatMatrix(List<List<Double>> values) {
        List<List<Float>> result = new ArrayList<>(values.size());
        for (List<Double> row : values) {
            result.add(toFloatList(row));
        }
        return result;
    }

    private static List<Float> toFloatList(List<Double> values) {
        List<Float> result = new ArrayList<>(values.size());
        for (Double value : values) {
            result.add(value == null ? null : value.floatValue());
        }
        return result;
    }

    private static String stripEmbeddingsSuffix(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/embeddings")) {
            return normalized.substring(0, normalized.length() - "/embeddings".length());
        }
        return normalized;
    }

    private static List<Double> parseEmbeddingVector(JsonNode embeddingNode) {
        List<Double> embedding = new ArrayList<>(embeddingNode.size());
        for (JsonNode value : embeddingNode) {
            embedding.add(value.doubleValue());
        }
        return embedding;
    }
}

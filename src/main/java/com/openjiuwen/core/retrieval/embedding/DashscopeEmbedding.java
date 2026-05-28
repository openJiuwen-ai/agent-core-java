/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * DashScope Multimodal Embedding Model Implementation.
 *
 * <p>Multimodal embedding client using Alibaba DashScope API.
 * Supports text, image, and video embedding.</p>
 *
 * <p>Reference resources:</p>
 * <ul>
 *   <li>https://help.aliyun.com/zh/model-studio/multimodal-embedding-api-reference</li>
 *   <li>https://www.alibabacloud.com/help/en/model-studio/multimodal-embedding-api-reference</li>
 * </ul>
 *
 * <p>Mirrors Python's DashscopeEmbedding in openjiuwen.core.retrieval.embedding.</p>
 */
public class DashscopeEmbedding extends APIEmbedding {

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Integer configuredDimension;
    private final boolean matryoshkaDimension;
    private final Semaphore limiter;
    private final ExecutorService asyncExecutor;

    /**
     * Create DashscopeEmbedding with default settings.
     *
     * @param config embedding configuration
     */
    public DashscopeEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, null);
    }

    /**
     * Create DashscopeEmbedding with custom settings.
     *
     * @param config embedding configuration
     * @param timeout request timeout in seconds
     * @param maxRetries maximum retry count
     * @param extraHeaders additional request headers
     * @param maxBatchSize maximum batch size for each query
     * @param maxConcurrent maximum number of concurrent requests
     * @param dimension embedding dimension for Matryoshka models
     * @param httpClient HTTP client (optional)
     */
    public DashscopeEmbedding(EmbeddingConfig config,
                              int timeout,
                              int maxRetries,
                              Map<String, String> extraHeaders,
                              int maxBatchSize,
                              int maxConcurrent,
                              Integer dimension,
                              HttpClient httpClient) {
        super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, httpClient);
        this.configuredDimension = dimension;
        this.matryoshkaDimension = dimension != null;
        this.limiter = new Semaphore(maxConcurrent);
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public int getDimension() {
        return configuredDimension != null ? configuredDimension : super.getDimension();
    }

    /**
     * Embed a single query text.
     *
     * @param text query text
     * @param options additional options
     * @return embedding vector
     */
    @Override
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        List<List<Float>> embeddings = embedDocuments(List.of(text), 1, withDimensions(options));
        return embeddings.isEmpty() ? new ArrayList<>() : embeddings.get(0);
    }

    /**
     * Embed multiple documents.
     *
     * @param texts document texts
     * @param batchSize batch size (optional)
     * @param options additional options
     * @return list of embedding vectors
     */
    @Override
    public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options) {
        List<String> nonEmpty = validateEmbedDocs(texts, options);

        int bsz = batchSize != null ? batchSize : (maxBatchSize > 0 ? maxBatchSize : 1);
        if (maxBatchSize > 0) {
            bsz = Math.min(bsz, maxBatchSize);
        }

        List<List<Float>> results = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i += bsz) {
            int j = Math.min(i + bsz, nonEmpty.size());
            List<String> batch = nonEmpty.subList(i, j);
            List<List<Float>> batchEmbeddings = getEmbeddingsSync(batch, options);
            results.addAll(batchEmbeddings);
        }

        return results;
    }

    /**
     * Async embed a single query.
     *
     * @param text query text
     * @param options additional options
     * @return CompletableFuture with embedding vector
     */
    public CompletableFuture<List<Float>> embedQueryAsync(String text, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedQuery(text, options), asyncExecutor);
    }

    /**
     * Async embed multiple documents.
     *
     * @param texts document texts
     * @param batchSize batch size
     * @param options additional options
     * @return CompletableFuture with list of embedding vectors
     */
    public CompletableFuture<List<List<Float>>> embedDocumentsAsync(List<String> texts, Integer batchSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedDocuments(texts, batchSize, options), asyncExecutor);
    }

    /**
     * Get embeddings synchronously for a batch.
     *
     * @param texts batch of texts
     * @param options additional options
     * @return list of embedding vectors
     */
    private List<List<Float>> getEmbeddingsSync(List<String> texts, Map<String, Object> options) {
        Map<String, Object> requestParams = buildRequestParams(texts, options);

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String requestBody = MAPPER.writeValueAsString(requestParams);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/services/embeddings/text-embedding/text-embedding"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(timeout))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseDashscopeEmbeddings(MAPPER.readTree(response.body()));
                }

                LOGGER.warning("DashscopeEmbedding request failed (attempt " + (attempt + 1) + "/" + maxRetries + "): HTTP " + response.statusCode());

            } catch (Exception e) {
                LOGGER.warning("DashscopeEmbedding request error (attempt " + (attempt + 1) + "/" + maxRetries + "): " + e.getMessage());
                if (attempt >= maxRetries - 1) {
                    throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED, "Failed after " + maxRetries + " attempts: " + e.getMessage());
                }
            }
        }

        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED, "Failed to get embeddings after retries");
    }

    /**
     * Build request parameters for DashScope API.
     *
     * @param texts input texts
     * @param options additional options
     * @return request parameters map
     */
    private Map<String, Object> buildRequestParams(List<String> texts, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", modelName);

        List<Map<String, Object>> inputList = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("text", texts.get(i));
            inputList.add(inputItem);
        }
        params.put("input", inputList);

        if (configuredDimension != null) {
            params.put("dimension", configuredDimension);
        }

        // Merge additional options
        if (options != null) {
            params.putAll(options);
        }

        return params;
    }

    /**
     * Parse DashScope API embeddings response.
     *
     * @param root JSON response root
     * @return list of embedding vectors
     */
    private List<List<Float>> parseDashscopeEmbeddings(JsonNode root) {
        if (root == null || !root.has("output")) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID, "No output in DashScope response");
        }

        JsonNode output = root.get("output");
        if (!output.has("embeddings")) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID, "No embeddings in DashScope output");
        }

        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : output.get("embeddings")) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode != null && embeddingNode.isArray()) {
                List<Float> embedding = new ArrayList<>();
                for (JsonNode value : embeddingNode) {
                    embedding.add(value.floatValue());
                }
                embeddings.add(embedding);
            }
        }

        return embeddings;
    }

    /**
     * Validate and filter non-empty documents.
     *
     * @param texts input texts
     * @param options additional options
     * @return filtered list of non-empty texts
     */
    private List<String> validateEmbedDocs(List<String> texts, Map<String, Object> options) {
        List<String> nonEmpty = new ArrayList<>();
        for (String text : texts) {
            if (text != null && !text.trim().isEmpty()) {
                nonEmpty.add(text);
            }
        }
        if (nonEmpty.isEmpty()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID, "All texts are empty");
        }
        return nonEmpty;
    }

    /**
     * Add dimension to options if configured.
     *
     * @param options original options
     * @return options with dimension
     */
    private Map<String, Object> withDimensions(Map<String, Object> options) {
        if (configuredDimension == null) {
            return options;
        }
        Map<String, Object> newOptions = options != null ? new LinkedHashMap<>(options) : new LinkedHashMap<>();
        newOptions.put("dimension", configuredDimension);
        return newOptions;
    }

    /**
     * Check if Matryoshka dimension is configured.
     *
     * @return true if Matryoshka dimension is configured
     */
    public boolean isMatryoshkaDimension() {
        return matryoshkaDimension;
    }

    @Override
    public void close() {
        super.close();
        asyncExecutor.shutdown();
    }
}
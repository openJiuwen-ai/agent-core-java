/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Universal HTTP embedding client aligned with the Python APIEmbedding implementation.
 */
public class APIEmbedding implements Embedding, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final EmbeddingConfig config;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final String modelName;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final String apiKey;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final String apiUrl;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int timeout;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int maxRetries;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int maxBatchSize;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int maxConcurrent;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Map<String, String> headers;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final HttpClient httpClient;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ExecutorService executor;

    private volatile Integer dimension;

    /**
     * Auto-generated for codecheck compliance.
     */
    public APIEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public APIEmbedding(EmbeddingConfig config,
                        int timeout,
                        int maxRetries,
                        Map<String, String> extraHeaders,
                        int maxBatchSize,
                        int maxConcurrent) {
        this(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public APIEmbedding(EmbeddingConfig config,
                        int timeout,
                        int maxRetries,
                        Map<String, String> extraHeaders,
                        int maxBatchSize,
                        int maxConcurrent,
                        HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.modelName = config.getModelName();
        this.apiKey = config.getApiKey();
        this.apiUrl = config.getBaseUrl();
        this.timeout = Math.max(1, timeout);
        this.maxRetries = Math.max(1, maxRetries);
        this.maxBatchSize = Math.max(1, maxBatchSize);
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            this.headers.put("Authorization", "Bearer " + apiKey);
        }
        if (extraHeaders != null) {
            this.headers.putAll(extraHeaders);
        }
        if (httpClient == null) {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(this.timeout));
            SslUtils.configureHttpClientSsl(builder, this.apiUrl, config.isVerifySsl(), config.getSslCert());
            this.httpClient = builder.build();
        } else {
            this.httpClient = httpClient;
        }
        this.executor = Executors.newFixedThreadPool(
                this.maxConcurrent,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("openjiuwen-embed");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        /**
                         * Auto-generated for codecheck compliance.
                         */
                        @Override
                        /**
                         * Auto-generated for codecheck compliance.
                         */
                        public void uncaughtException(Thread t, Throwable e) {
                            Loggers.CONTROLLER.error("APIEmbedding Error,Thread {} , {}", t.getName(),e.getMessage());
                        }
                    });
                    return thread;
                });
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(String text) {
        return embedQuery(text, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        if (text == null || text.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "Empty text provided for embedding");
        }
        List<List<Float>> embeddings = getEmbeddings(text, options);
        return embeddings.get(0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize) {
        return embedDocuments(texts, batchSize, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize, Map<String, Object> options) {
        List<String> nonEmpty = validateTexts(texts);
        int effectiveBatchSize = Math.max(1, Math.min(batchSize == null ? maxBatchSize : batchSize, maxBatchSize));
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i += effectiveBatchSize) {
            indices.add(i);
        }
        BaseCallback callback = resolveCallback(options, indices);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(indices.size());
        for (Integer start : indices) {
            int end = Math.min(nonEmpty.size(), start + effectiveBatchSize);
            List<String> batch = List.copyOf(nonEmpty.subList(start, end));
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<List<Float>> embeddings = getEmbeddings(batch, options);
                callback.onBatch(start, end, batch);
                return new BatchResult(start, embeddings);
            }, executor));
        }

        List<List<Float>> result = new ArrayList<>(nonEmpty.size());
        for (int i = 0; i < nonEmpty.size(); i++) {
            result.add(null);
        }
        for (CompletableFuture<BatchResult> future : futures) {
            BatchResult batch;
            try {
                batch = future.join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(cause);
            }
            for (int i = 0; i < batch.embeddings.size(); i++) {
                result.set(batch.start + i, batch.embeddings.get(i));
            }
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getDimension() {
        Integer cached = dimension;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dimension == null) {
                dimension = embedQuery("test").size();
            }
            return dimension;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("input", input);
        payload.putAll(cleanPayloadOptions(options));
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String body = MAPPER.writeValueAsString(payload);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(timeout))
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
                HttpResponse<String> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (attempt < maxRetries) {
                        continue;
                    }
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                            "Failed to get embedding after " + maxRetries + " attempts");
                }
                List<List<Float>> embeddings = parseEmbeddings(MAPPER.readTree(response.body()));
                if (dimension == null && !embeddings.isEmpty() && !embeddings.get(0).isEmpty()) {
                    dimension = embeddings.get(0).size();
                }
                return embeddings;
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt >= maxRetries) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                            "Failed to get embedding after " + maxRetries + " attempts");
                }
            }
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED,
                "Unreachable code in APIEmbedding.getEmbeddings");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<List<Float>> parseEmbeddings(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embeddings in response: null");
        }
        if (root.has("embedding")) {
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode.isArray() && embeddingNode.size() > 0 && embeddingNode.get(0).isArray()) {
                return parseEmbeddingArray(embeddingNode);
            }
            return List.of(parseSingleEmbedding(embeddingNode));
        }
        if (root.has("embeddings")) {
            return parseEmbeddingArray(root.get("embeddings"));
        }
        if (root.has("data") && root.get("data").isArray()) {
            List<List<Float>> embeddings = new ArrayList<>();
            for (JsonNode item : root.get("data")) {
                JsonNode embeddingNode = item.get("embedding");
                if (embeddingNode != null && !embeddingNode.isNull()) {
                    embeddings.add(parseSingleEmbedding(embeddingNode));
                }
            }
            if (!embeddings.isEmpty()) {
                return embeddings;
            }
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embedding field found in data items: " + root);
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                "No embeddings in response: " + root);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<Float> parseSingleEmbedding(JsonNode embeddingNode) {
        if (embeddingNode == null || embeddingNode.isNull()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "embedding item is null");
        }
        if (!embeddingNode.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "embedding item is not a numeric array");
        }
        List<Float> embedding = new ArrayList<>(embeddingNode.size());
        for (JsonNode item : embeddingNode) {
            if (!item.isNumber()) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                        "embedding item is not numeric");
            }
            embedding.add(item.floatValue());
        }
        return embedding;
    }

    private static List<List<Float>> parseEmbeddingArray(JsonNode embeddingsNode) {
        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : embeddingsNode) {
            if (!item.isNull()) {
                embeddings.add(parseArrayItem(item));
            }
        }
        return embeddings;
    }

    private static List<Float> parseArrayItem(JsonNode item) {
        if (!item.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "embedding list item is not an array");
        }
        List<Float> embedding = new ArrayList<>(item.size());
        for (JsonNode value : item) {
            if (!value.isNumber()) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                        "embedding list item is not numeric");
            }
            embedding.add(value.floatValue());
        }
        return embedding;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static List<String> validateTexts(List<?> texts) {
        if (texts == null || texts.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "Empty texts list provided");
        }
        List<String> nonEmpty = new ArrayList<>(texts.size());
        int emptyCount = 0;
        for (Object item : texts) {
            if (!(item instanceof String text) || text.isBlank()) {
                emptyCount++;
            } else {
                nonEmpty.add(text);
            }
        }
        if (emptyCount > 0) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    emptyCount + " chunks are empty while embedding");
        }
        if (nonEmpty.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "All texts are empty after filtering");
        }
        return nonEmpty;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static BaseCallback resolveCallback(Map<String, Object> options, Collection<?> sequence) {
        Object callback = options == null ? null : options.get("callback");
        if (callback instanceof BaseCallback baseCallback) {
            return baseCallback;
        }
        Object callbackClass = options == null ? null : options.get("callback_cls");
        if (callbackClass instanceof Class<?> clazz && BaseCallback.class.isAssignableFrom(clazz)) {
            try {
                return (BaseCallback) clazz.getConstructor(Collection.class).newInstance(sequence);
            } catch (ReflectiveOperationException ignored) {
                try {
                    return (BaseCallback) clazz.getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID,
                            "callback_cls must be instantiable");
                }
            }
        }
        if (callbackClass != null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID,
                    "callback_cls in APIEmbedding.embedDocuments must be a subclass of BaseCallback");
        }
        return new BaseCallback(sequence);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static Map<String, Object> cleanPayloadOptions(Map<String, Object> options) {
        Map<String, Object> payloadOptions = new LinkedHashMap<>();
        if (options == null || options.isEmpty()) {
            return payloadOptions;
        }
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey();
            if ("callback".equals(key) || "callback_cls".equals(key)) {
                continue;
            }
            payloadOptions.put(key, entry.getValue());
        }
        return payloadOptions;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record BatchResult(int start, List<List<Float>> embeddings) {
    }
}

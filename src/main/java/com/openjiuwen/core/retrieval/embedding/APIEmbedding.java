/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.BaseCallback;

import javax.net.ssl.SSLParameters;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors Python's {@code APIEmbedding} in
 * {@code openjiuwen/core/retrieval/embedding/api_embedding.py}.
 */
public class APIEmbedding extends Embedding implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final String EMBEDDING_SSL_VERIFY = "EMBEDDING_SSL_VERIFY";
    private static final String EMBEDDING_SSL_CERT = "EMBEDDING_SSL_CERT";

    protected final EmbeddingConfig config;
    protected final String modelName;
    protected final String apiKey;
    protected String apiUrl;
    protected final int timeout;
    protected final int maxRetries;
    protected final int maxBatchSize;
    protected final int maxConcurrent;
    protected final Map<String, String> headers;
    protected final ThreadPoolExecutor executor;
    protected final HttpClient httpClient;

    private volatile Integer dimension;

    public APIEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null);
    }

    public APIEmbedding(EmbeddingConfig config,
                        int timeout,
                        int maxRetries,
                        Map<String, String> extraHeaders,
                        int maxBatchSize,
                        int maxConcurrent) {
        this(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, null);
    }

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
        this.apiUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl();
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.maxBatchSize = maxBatchSize;
        this.maxConcurrent = maxConcurrent;
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (this.apiKey != null && !this.apiKey.isBlank()) {
            this.headers.put("Authorization", "Bearer " + this.apiKey);
        }
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            this.headers.putAll(extraHeaders);
        }
        this.limiter = new Semaphore(maxConcurrent);
        // 线程名前缀保持 openjiuwen_embed-，对齐历史实现与 Python 侧命名习惯
        this.executor = (ThreadPoolExecutor) OpenJiuwenExecutors.newFixedThreadPool(
                "openjiuwen_embed", this.maxConcurrent, true);
        this.httpClient = httpClient == null ? createHttpClient() : httpClient;
    }

    @Override
    public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
        validateEmbedQueryText(text);
        return CompletableFuture.supplyAsync(() -> embedQuerySync(text, kwargs), executor);
    }

    public List<Double> embedQuerySync(String text) {
        return embedQuerySync(text, Map.of());
    }

    public List<Double> embedQuerySync(String text, Map<String, Object> kwargs) {
        validateEmbedQueryText(text);
        List<List<Double>> embeddings = getEmbeddingsSync(text, kwargs);
        return embeddings.get(0);
    }

    @Override
    public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                Integer batchSize,
                                                                Map<String, Object> kwargs) {
        List<String> nonEmpty = validateEmbedDocs(texts, kwargs);
        int effectiveBatchSize = resolveBatchSize(batchSize);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i += effectiveBatchSize) {
            indices.add(i);
        }
        BaseCallback callback = resolveCallback(kwargs, indices);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(indices.size());
        for (Integer startIdx : indices) {
            futures.add(processBatchAsync(nonEmpty, startIdx, effectiveBatchSize, kwargs, callback, false));
        }
        return collectBatchResults(nonEmpty.size(), futures);
    }

    public List<List<Double>> embedDocumentsSync(List<String> texts) {
        return embedDocumentsSync(texts, null, Map.of());
    }

    public List<List<Double>> embedDocumentsSync(List<String> texts,
                                                 Integer batchSize,
                                                 Map<String, Object> kwargs) {
        List<String> nonEmpty = validateEmbedDocs(texts, kwargs);
        int effectiveBatchSize = resolveBatchSize(batchSize);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i += effectiveBatchSize) {
            indices.add(i);
        }
        BaseCallback callback = resolveCallback(kwargs, indices);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(indices.size());
        for (Integer startIdx : indices) {
            futures.add(processBatchAsync(nonEmpty, startIdx, effectiveBatchSize, kwargs, callback, true));
        }
        return collectBatchResults(nonEmpty.size(), futures).join();
    }

    @Override
    public int getDimension() {
        Integer cached = dimension;
        if (cached != null) {
            return cached;
        }
        int inferred = embedQuerySync("test").size();
        synchronized (this) {
            if (dimension == null) {
                dimension = inferred;
                LOGGER.debug("Determined embedding dimension: {}", dimension);
            }
            return dimension;
        }
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    protected CompletableFuture<BatchResult> processBatchAsync(List<String> texts,
                                                               int startIdx,
                                                               int batchSize,
                                                               Map<String, Object> kwargs,
                                                               BaseCallback callback,
                                                               boolean syncCallbackUsesEmbeddings) {
        return CompletableFuture.supplyAsync(() -> {
            acquirePermit();
            try {
                int endIdx = Math.min(texts.size(), startIdx + batchSize);
                List<String> batchTexts = List.copyOf(texts.subList(startIdx, endIdx));
                List<List<Double>> embeddings = getEmbeddingsSync(batchTexts, kwargs);
                if (syncCallbackUsesEmbeddings) {
                    callback.call(startIdx, endIdx, stringifyEmbeddings(embeddings));
                } else {
                    callback.call(startIdx, endIdx, batchTexts);
                }
                return new BatchResult(startIdx, embeddings);
            } finally {
                limiter.release();
            }
        }, executor);
    }

    protected List<List<Double>> getEmbeddingsSync(Object textOrTexts, Map<String, Object> kwargs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("input", textOrTexts);
        payload.putAll(cleanPayloadOptions(kwargs));

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                ApiResponse response = doRequest(payload);
                List<List<Double>> embeddings = parseEmbeddings(response.body());
                if (dimension == null && !embeddings.isEmpty() && !embeddings.get(0).isEmpty()) {
                    dimension = embeddings.get(0).size();
                    LOGGER.debug("Determined embedding dimension: {}", dimension);
                }
                return embeddings;
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt == maxRetries - 1) {
                    throw buildRequestFailedError(exception);
                }
                LOGGER.warning(
                        "Embedding request failed (attempt {}/{}): {}",
                        attempt + 1,
                        maxRetries,
                        exception.getMessage()
                );
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED,
                "error_msg",
                "Unreachable code in _get_embeddings_sync"
        );
    }

    protected ApiResponse doRequest(Map<String, Object> payload) throws IOException, InterruptedException {
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return new ApiResponse(response.statusCode(), response.body());
    }

    protected List<List<Double>> parseEmbeddings(String body) throws IOException {
        JsonNode result = OBJECT_MAPPER.readTree(body);
        if (result.has("embedding")) {
            JsonNode embeddingNode = result.get("embedding");
            if (embeddingNode.isArray() && embeddingNode.size() > 0 && embeddingNode.get(0).isArray()) {
                return parseEmbeddingMatrix(embeddingNode);
            }
            return List.of(parseEmbeddingVector(embeddingNode));
        }
        if (result.has("embeddings")) {
            return parseEmbeddingMatrix(result.get("embeddings"));
        }
        if (result.has("data") && result.get("data").isArray()) {
            List<List<Double>> embeddings = new ArrayList<>();
            for (JsonNode item : result.get("data")) {
                if (item.has("embedding")) {
                    embeddings.add(parseEmbeddingVector(item.get("embedding")));
                }
            }
            if (!embeddings.isEmpty()) {
                return embeddings;
            }
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "No embeddings field found in data items: " + result
            );
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                "error_msg",
                "No embeddings in response: " + result
        );
    }

    public static List<String> validateEmbedDocs(List<String> texts, Map<String, Object> kwargs) {
        if (texts == null || texts.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    "Empty texts list provided"
            );
        }
        Object callbackCls = kwargs == null ? null : kwargs.get("callback_cls");
        if (callbackCls != null && (!(callbackCls instanceof Class<?> clazz)
                || !BaseCallback.class.isAssignableFrom(clazz))) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID,
                    "error_msg",
                    "callback_cls in APIEmbedding.embed_documents must be a subclass of BaseCallback"
            );
        }

        List<String> nonEmpty = new ArrayList<>(texts.size());
        int emptyCount = 0;
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                nonEmpty.add(text);
            } else {
                emptyCount++;
            }
        }
        if (emptyCount > 0) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    emptyCount + " chunks are empty while embedding"
            );
        }
        if (nonEmpty.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    "All texts are empty after filtering"
            );
        }
        return nonEmpty;
    }

    private static void validateEmbedQueryText(String text) {
        if (text == null || text.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    "Empty text provided for embedding"
            );
        }
    }

    protected static BaseCallback resolveCallback(Map<String, Object> kwargs, Collection<?> sequence) {
        if (kwargs == null || kwargs.isEmpty()) {
            return new BaseCallback(sequence);
        }
        Object callback = kwargs.get("callback");
        if (callback instanceof BaseCallback baseCallback) {
            return baseCallback;
        }
        Object callbackCls = kwargs.get("callback_cls");
        if (callbackCls == null) {
            return new BaseCallback(sequence);
        }
        Class<?> callbackClass = (Class<?>) callbackCls;
        try {
            try {
                return (BaseCallback) callbackClass.getConstructor(Iterable.class).newInstance(sequence);
            } catch (ReflectiveOperationException ignored) {
                if (sequence instanceof List<?> list) {
                    try {
                        return (BaseCallback) callbackClass.getConstructor(List.class, boolean.class, String.class)
                                .newInstance(list, false, null);
                    } catch (ReflectiveOperationException innerIgnored) {
                        return (BaseCallback) callbackClass.getConstructor().newInstance();
                    }
                }
                return (BaseCallback) callbackClass.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID,
                    "error_msg",
                    "callback_cls must be instantiable"
            );
        }
    }

    protected static Map<String, Object> cleanPayloadOptions(Map<String, Object> kwargs) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (kwargs == null || kwargs.isEmpty()) {
            return options;
        }
        for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
            if ("callback".equals(entry.getKey()) || "callback_cls".equals(entry.getKey())) {
                continue;
            }
            options.put(entry.getKey(), entry.getValue());
        }
        return options;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout));
        if (apiUrl.startsWith("https://")) {
            boolean disableVerify = boolEnv(EMBEDDING_SSL_VERIFY, List.of("false"));
            String sslCert = System.getenv(EMBEDDING_SSL_CERT);
            if (disableVerify) {
                builder.sslContext(SslUtils.createInsecureSslContext());
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("");
                builder.sslParameters(sslParameters);
            } else if (sslCert != null && !sslCert.isBlank()) {
                builder.sslContext(SslUtils.createStrictSslContext(sslCert));
            }
        }
        return builder.build();
    }

    private static boolean boolEnv(String envName, List<String> triggerValues) {
        String value = System.getenv(envName);
        if (value == null) {
            return false;
        }
        for (String triggerValue : triggerValues) {
            if (value.trim().equalsIgnoreCase(triggerValue)) {
                return true;
            }
        }
        return false;
    }

    private void acquirePermit() {
        try {
            limiter.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    private int resolveBatchSize(Integer batchSize) {
        int requested = batchSize == null ? maxBatchSize : batchSize;
        return Math.min(Math.max(requested, 1), maxBatchSize);
    }

    private CompletableFuture<List<List<Double>>> collectBatchResults(int totalSize,
                                                                      List<CompletableFuture<BatchResult>> futures) {
        CompletableFuture<?>[] futureArray = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futureArray).thenApply(ignored -> {
            List<List<Double>> allEmbeddings = new ArrayList<>(totalSize);
            for (int i = 0; i < totalSize; i++) {
                allEmbeddings.add(null);
            }
            for (CompletableFuture<BatchResult> future : futures) {
                BatchResult result = future.join();
                for (int i = 0; i < result.embeddings().size(); i++) {
                    allEmbeddings.set(result.startIdx() + i, result.embeddings().get(i));
                }
            }
            return allEmbeddings;
        });
    }

    private BaseError buildRequestFailedError(Exception exception) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                null,
                null,
                exception,
                Map.of("error_msg", "Failed to get embedding after " + maxRetries + " attempts")
        );
    }

    private static List<List<Double>> parseEmbeddingMatrix(JsonNode embeddingNode) {
        List<List<Double>> embeddings = new ArrayList<>(embeddingNode.size());
        for (JsonNode item : embeddingNode) {
            embeddings.add(parseEmbeddingVector(item));
        }
        return embeddings;
    }

    private static List<Double> parseEmbeddingVector(JsonNode embeddingNode) {
        List<Double> embedding = new ArrayList<>(embeddingNode.size());
        for (JsonNode value : embeddingNode) {
            embedding.add(value.doubleValue());
        }
        return embedding;
    }

    private static List<String> stringifyEmbeddings(List<List<Double>> embeddings) {
        List<String> values = new ArrayList<>(embeddings.size());
        for (List<Double> embedding : embeddings) {
            values.add(String.valueOf(embedding));
        }
        return values;
    }

    protected record ApiResponse(int statusCode, String body) {
    }

    protected record BatchResult(int startIdx, List<List<Double>> embeddings) {
    }
}

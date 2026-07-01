/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;

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
 * DashScope multimodal embedding client.
 * <p>
 * Mirrors Python's {@code DashscopeEmbedding} in
 * {@code openjiuwen/core/retrieval/embedding/dashscope_embedding.py}.
 * </p>
 */
public class DashscopeEmbedding extends APIEmbedding {

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Integer configuredDimension;
    private final boolean matryoshkaDimension;
    private final Semaphore limiter;
    private final ExecutorService asyncExecutor;
    private final Map<String, Object> requestParams;
    private volatile Integer inferredDimension;

    public DashscopeEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, null);
    }

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
        this.limiter = new Semaphore(Math.max(1, maxConcurrent));
        this.asyncExecutor = Executors.newCachedThreadPool();
        this.requestParams = new LinkedHashMap<>();
        this.requestParams.put("model", modelName);
        this.requestParams.put("api_key", apiKey);
        this.requestParams.put("base_address", apiUrl);
        this.requestParams.put("timeout", this.timeout);
        if (dimension != null) {
            this.requestParams.put("dimension", dimension);
        }
    }

    @Override
    public int getDimension() {
        if (configuredDimension != null) {
            return configuredDimension;
        }
        Integer cachedDimension = inferredDimension;
        return cachedDimension != null ? cachedDimension : super.getDimension();
    }

    public Map<String, Object> getRequestParams() {
        return new LinkedHashMap<>(requestParams);
    }

    @Override
    public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> embedQuerySync(text, kwargs), asyncExecutor);
    }

    @Override
    public List<Double> embedQuerySync(String text, Map<String, Object> kwargs) {
        List<List<Double>> embeddings = embedDocumentsSync(List.of(text), 1, withDimensions(kwargs));
        return embeddings.get(0);
    }

    @Override
    public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                Integer batchSize,
                                                                Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> embedDocumentsSync(texts, batchSize, kwargs), asyncExecutor);
    }

    @Override
    public List<List<Double>> embedDocumentsSync(List<String> texts,
                                                 Integer batchSize,
                                                 Map<String, Object> kwargs) {
        List<Object> nonEmpty = normalizeInputs(validateEmbedDocs(texts));
        int effectiveBatchSize = batchSize != null ? batchSize : (maxBatchSize > 0 ? maxBatchSize : 1);
        if (maxBatchSize > 0) {
            effectiveBatchSize = Math.min(effectiveBatchSize, maxBatchSize);
        }

        List<List<Double>> results = new ArrayList<>();
        for (int start = 0; start < nonEmpty.size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, nonEmpty.size());
            List<Object> batch = new ArrayList<>(nonEmpty.subList(start, end));
            results.addAll(getEmbeddingsSync(batch, kwargs));
        }
        return results;
    }

    public List<Double> embedMultimodalSync(MultimodalDocument document, Map<String, Object> kwargs) {
        if (document == null) {
            throw invalidMultimodalInput();
        }
        List<List<Double>> embeddings = getEmbeddingsSync(List.of(document.getDashscopeInput()), kwargs);
        return embeddings.get(0);
    }

    public List<Double> embedMultimodalSync(Object document, Map<String, Object> kwargs) {
        if (!(document instanceof MultimodalDocument multimodalDocument)) {
            throw invalidMultimodalInput();
        }
        return embedMultimodalSync(multimodalDocument, kwargs);
    }

    public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document,
                                                           Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> embedMultimodalSync(document, kwargs), asyncExecutor);
    }

    public CompletableFuture<List<Double>> embedMultimodal(Object document, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> embedMultimodalSync(document, kwargs), asyncExecutor);
    }

    protected List<List<Double>> getEmbeddingsSync(List<?> texts, Map<String, Object> kwargs) {
        Map<String, Object> params = buildRequestParams(texts, kwargs);

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                limiter.acquire();
                try {
                    String body = MAPPER.writeValueAsString(params);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl + "/services/embeddings/text-embedding/text-embedding"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(timeout))
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonNode root = MAPPER.readTree(response.body());
                    JsonNode output = root.has("output") ? root.get("output") : root;
                    List<List<Double>> embeddings = handleDashscopeApiResponse(
                            response.statusCode(),
                            root.path("code").asText(null),
                            root.path("message").asText("HTTP " + response.statusCode()),
                            output,
                            attempt
                    );
                    if (embeddings != null) {
                        return embeddings;
                    }
                    LOGGER.warning(
                            "DashscopeEmbedding request failed (attempt {}/{}): HTTP {}",
                            attempt + 1,
                            maxRetries,
                            response.statusCode()
                    );
                    if (attempt >= maxRetries - 1) {
                        throw ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                                "error_msg",
                                "Failed to get embedding after " + maxRetries + " attempts: HTTP "
                                        + response.statusCode()
                        );
                    }
                } finally {
                    limiter.release();
                }
            } catch (BaseError error) {
                throw error;
            } catch (Exception exception) {
                LOGGER.warning(
                        "DashscopeEmbedding request error (attempt {}/{}): {}",
                        attempt + 1,
                        maxRetries,
                        exception.getMessage()
                );
                if (attempt >= maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed after " + maxRetries + " attempts: " + exception.getMessage()
                    );
                }
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED,
                "error_msg",
                "Unreachable code in DashscopeEmbedding.getEmbeddingsSync"
        );
    }

    protected Map<String, Object> buildRequestParams(List<?> texts, Map<String, Object> kwargs) {
        Map<String, Object> params = new LinkedHashMap<>(requestParams);
        params.put("input", normalizeInputs(texts));
        if (kwargs != null) {
            Map<String, Object> extra = new LinkedHashMap<>(kwargs);
            extra.remove("callback");
            extra.remove("callback_cls");
            params.putAll(extra);
        }
        return params;
    }

    protected List<List<Double>> parseDashscopeEmbeddings(JsonNode root) {
        if (root == null || !root.has("output")) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "No output in DashScope response"
            );
        }
        return handleDashscopeApiResponse(200, null, null, root.path("output"), 0);
    }

    protected List<List<Double>> handleDashscopeApiResponse(int statusCode,
                                                            String errorCode,
                                                            String errorMessage,
                                                            JsonNode output,
                                                            int attempt) {
        if (statusCode != 200 && attempt >= maxRetries - 1) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                    "error_msg",
                    "Failed to get embedding after " + maxRetries + " attempts: " + errorMessage
            );
        }
        JsonNode embeddingItems = output == null ? null : output.path("embeddings");
        if (!embeddingItems.isArray()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "No embeddings in response: " + output
            );
        }

        List<JsonNode> sortedItems = new ArrayList<>();
        embeddingItems.forEach(sortedItems::add);
        sortedItems.sort((left, right) -> Integer.compare(left.path("index").asInt(0), right.path("index").asInt(0)));

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : sortedItems) {
            JsonNode embeddingNode = item.path("embedding");
            if (!embeddingNode.isArray()) {
                continue;
            }
            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.doubleValue());
            }
            embeddings.add(embedding);
        }
        if (embeddings.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "error_msg",
                    "The embeddings field in response is empty: " + output
            );
        }
        if (inferredDimension == null && !embeddings.get(0).isEmpty()) {
            inferredDimension = embeddings.get(0).size();
            LOGGER.debug("Determined embedding dimension: {}", inferredDimension);
        }
        return embeddings;
    }

    private List<Object> validateEmbedDocs(List<?> texts) {
        if (texts == null || texts.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "error_msg",
                    "Empty texts list provided"
            );
        }
        List<Object> nonEmpty = new ArrayList<>();
        int emptyCount = 0;
        for (Object value : texts) {
            if (value == null) {
                emptyCount++;
            } else if (value instanceof String text) {
                if (text.isBlank()) {
                    emptyCount++;
                } else {
                    nonEmpty.add(text);
                }
            } else if (value instanceof MultimodalDocument document) {
                if (document.getContent().isEmpty()) {
                    emptyCount++;
                } else {
                    nonEmpty.add(document);
                }
            } else {
                nonEmpty.add(value);
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

    private Map<String, Object> withDimensions(Map<String, Object> kwargs) {
        if (configuredDimension == null) {
            return kwargs == null ? Map.of() : kwargs;
        }
        Map<String, Object> result = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        result.put("dimension", configuredDimension);
        return result;
    }

    private static List<Object> normalizeInputs(List<?> texts) {
        List<Object> inputList = new ArrayList<>();
        for (Object value : texts) {
            if (value instanceof MultimodalDocument document) {
                inputList.add(document.getDashscopeInput());
            } else {
                inputList.add(value);
            }
        }
        return inputList;
    }

    public boolean isMatryoshkaDimension() {
        return matryoshkaDimension;
    }

    private static BaseError invalidMultimodalInput() {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                "error_msg",
                "input provided for multimodal embedding is not a MultimodalDocument"
        );
    }

    @Override
    public void close() {
        super.close();
        asyncExecutor.shutdown();
    }
}

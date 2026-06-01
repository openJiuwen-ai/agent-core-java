/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
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
 * DashScope multimodal embedding client.
 *
 * <p>Mirrors Python's {@code DashscopeEmbedding} in
 * {@code openjiuwen.core.retrieval.embedding.dashscope_embedding}.</p>
 */
public class DashscopeEmbedding extends APIEmbedding {

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Integer configuredDimension;
    private final boolean matryoshkaDimension;
    private final Semaphore limiter;
    private final ExecutorService asyncExecutor;
    private final Map<String, Object> requestParams;

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
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
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
        return configuredDimension != null ? configuredDimension : super.getDimension();
    }

    public Map<String, Object> getRequestParams() {
        return new LinkedHashMap<>(requestParams);
    }

    @Override
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        List<List<Float>> embeddings = embedDocuments(List.of(text), 1, withDimensions(options));
        return embeddings.getFirst();
    }

    @Override
    public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options) {
        List<Object> nonEmpty = validateEmbedDocs(texts);
        int effectiveBatchSize = batchSize != null ? batchSize : (maxBatchSize > 0 ? maxBatchSize : 1);
        if (maxBatchSize > 0) {
            effectiveBatchSize = Math.min(effectiveBatchSize, maxBatchSize);
        }

        List<List<Float>> results = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i += effectiveBatchSize) {
            int end = Math.min(i + effectiveBatchSize, nonEmpty.size());
            List<Object> batch = new ArrayList<>(nonEmpty.subList(i, end));
            results.addAll(getEmbeddingsSync(batch, options));
        }
        return results;
    }

    public List<List<Float>> embedDocumentsSync(List<String> texts, Integer batchSize, Map<String, Object> options) {
        return embedDocuments(texts, batchSize, options);
    }

    public List<Float> embedMultimodal(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        List<List<Float>> embeddings = getEmbeddingsSync(List.of(document.getDashscopeInput()), options);
        return embeddings.getFirst();
    }

    public List<Float> embedMultimodal(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    public List<Float> embedMultimodalSync(Object input, Map<String, Object> options) {
        return embedMultimodal(input, options);
    }

    public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    public CompletableFuture<List<Float>> embedQueryAsync(String text, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedQuery(text, options), asyncExecutor);
    }

    public CompletableFuture<List<List<Float>>> embedDocumentsAsync(List<String> texts,
                                                                    Integer batchSize,
                                                                    Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedDocuments(texts, batchSize, options), asyncExecutor);
    }

    public CompletableFuture<List<Float>> embedMultimodalAsync(MultimodalDocument document,
                                                               Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> embedMultimodalSync(document, options), asyncExecutor);
    }

    protected List<List<Float>> getEmbeddingsSync(List<?> texts, Map<String, Object> options) {
        Map<String, Object> params = buildRequestParams(texts, options);

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
                    if (response.statusCode() == 200) {
                        return parseDashscopeEmbeddings(MAPPER.readTree(response.body()));
                    }
                    LOGGER.warning("DashscopeEmbedding request failed (attempt " + (attempt + 1)
                            + "/" + maxRetries + "): HTTP " + response.statusCode());
                    if (attempt >= maxRetries - 1) {
                        throw RetrievalExceptions.error(
                                StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                                "Failed to get embedding after " + maxRetries + " attempts: HTTP "
                                        + response.statusCode());
                    }
                } finally {
                    limiter.release();
                }
            } catch (Exception e) {
                LOGGER.warning("DashscopeEmbedding request error (attempt " + (attempt + 1)
                        + "/" + maxRetries + "): " + e.getMessage());
                if (attempt >= maxRetries - 1) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                            "Failed after " + maxRetries + " attempts: " + e.getMessage());
                }
            }
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED,
                "Unreachable code in DashscopeEmbedding.getEmbeddingsSync");
    }

    protected Map<String, Object> buildRequestParams(List<?> texts, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>(requestParams);
        params.put("input", normalizeInputs(texts));
        if (options != null) {
            Map<String, Object> extra = new LinkedHashMap<>(options);
            extra.remove("callback");
            extra.remove("callback_cls");
            params.putAll(extra);
        }
        return params;
    }

    protected List<List<Float>> parseDashscopeEmbeddings(JsonNode root) {
        if (root == null || !root.has("output")) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No output in DashScope response");
        }
        JsonNode embeddingItems = root.path("output").path("embeddings");
        if (!embeddingItems.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embeddings in DashScope output");
        }

        List<JsonNode> sortedItems = new ArrayList<>();
        embeddingItems.forEach(sortedItems::add);
        sortedItems.sort((left, right) -> Integer.compare(left.path("index").asInt(0), right.path("index").asInt(0)));

        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode item : sortedItems) {
            JsonNode embeddingNode = item.path("embedding");
            if (!embeddingNode.isArray()) {
                continue;
            }
            List<Float> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.floatValue());
            }
            embeddings.add(embedding);
        }
        if (embeddings.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "The embeddings field in response is empty: " + root);
        }
        cacheDimensionFrom(embeddings);
        return embeddings;
    }

    private List<Object> validateEmbedDocs(List<?> texts) {
        if (texts == null || texts.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "Empty texts list provided");
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

    private Map<String, Object> withDimensions(Map<String, Object> options) {
        if (configuredDimension == null) {
            return options;
        }
        Map<String, Object> result = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
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

    private void cacheDimensionFrom(List<List<Float>> embeddings) {
        if (configuredDimension != null || embeddings.isEmpty() || embeddings.getFirst().isEmpty()) {
            return;
        }
        try {
            java.lang.reflect.Field field = APIEmbedding.class.getDeclaredField("dimension");
            field.setAccessible(true);
            if (field.get(this) == null) {
                field.set(this, embeddings.getFirst().size());
            }
        } catch (ReflectiveOperationException ignored) {
            // The dimension cache is an optimization; parsing remains correct without it.
        }
    }

    public boolean isMatryoshkaDimension() {
        return matryoshkaDimension;
    }

    @Override
    public void close() {
        super.close();
        asyncExecutor.shutdown();
    }
}

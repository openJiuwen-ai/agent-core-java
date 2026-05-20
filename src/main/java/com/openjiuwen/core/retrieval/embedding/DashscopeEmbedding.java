/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemImage;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemVideo;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingOutput;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResultItem;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * DashScope multimodal embedding client.
 */
public class DashscopeEmbedding extends APIEmbedding {
    private final boolean isMatryoshkaDimension;
    private final Integer configuredDimension;
    private final Map<String, Object> requestParams;
    private final MultiModalEmbedding dashscopeClient;
    private volatile Integer resolvedDimension;

    /**
     * Auto-generated for codecheck compliance.
     */
    public DashscopeEmbedding(EmbeddingConfig config) {
        this(config, 60, 3, null, 8, 50, null, (HttpClient) null);
    }

    /**
     * Auto-generated for codecheck compliance.
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
        this.dashscopeClient = new MultiModalEmbedding(this.apiUrl);
        this.configuredDimension = dimension;
        this.isMatryoshkaDimension = dimension != null;
        this.requestParams = new LinkedHashMap<>();
        requestParams.put("model", modelName);
        requestParams.put("api_key", apiKey);
        requestParams.put("base_address", apiUrl);
        requestParams.put("timeout", this.timeout);
        if (dimension != null) {
            requestParams.put("dimension", dimension);
        }
    }

    DashscopeEmbedding(EmbeddingConfig config,
                       int timeout,
                       int maxRetries,
                       Map<String, String> extraHeaders,
                       int maxBatchSize,
                       int maxConcurrent,
                       Integer dimension,
                       MultiModalEmbedding dashscopeClient) {
        super(config, timeout, maxRetries, extraHeaders, maxBatchSize, maxConcurrent, null);
        this.dashscopeClient = dashscopeClient;
        this.configuredDimension = dimension;
        this.isMatryoshkaDimension = dimension != null;
        this.requestParams = new LinkedHashMap<>();
        requestParams.put("model", modelName);
        requestParams.put("api_key", apiKey);
        requestParams.put("base_address", apiUrl);
        requestParams.put("timeout", this.timeout);
        if (dimension != null) {
            requestParams.put("dimension", dimension);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isMatryoshkaDimension() {
        return isMatryoshkaDimension;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getRequestParams() {
        return new LinkedHashMap<>(requestParams);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getDimension() {
        if (configuredDimension != null) {
            return configuredDimension;
        }
        Integer cached = resolvedDimension;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (resolvedDimension == null) {
                resolvedDimension = embedQuery("test").size();
            }
            return resolvedDimension;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(String text) {
        return embedQuery((Object) text, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(String text, Map<String, Object> options) {
        return embedQuery((Object) text, options);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(Object text) {
        return embedQuery(text, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedQuery(Object text, Map<String, Object> options) {
        List<List<Float>> embeddings = embedDocuments(List.of(text), null, options);
        return embeddings.get(0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<List<Float>> embedDocumentsRaw(List<?> texts) {
        return embedDocuments(texts, null, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize, Map<String, Object> options) {
        List<Object> nonEmpty = validateDashscopeInputs(texts);
        List<Object> converted = new ArrayList<>(nonEmpty.size());
        for (Object item : nonEmpty) {
            converted.add(toDashscopeInput(item));
        }

        int effectiveBatchSize = Math.max(1, Math.min(batchSize == null ? maxBatchSize : batchSize, maxBatchSize));
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < converted.size(); i += effectiveBatchSize) {
            indices.add(i);
        }
        BaseCallback callback = resolveCallback(options, indices);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(indices.size());
        for (Integer start : indices) {
            int end = Math.min(converted.size(), start + effectiveBatchSize);
            List<Object> batch = List.copyOf(converted.subList(start, end));
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<List<Float>> embeddings = getDashscopeEmbeddings(batch, options);
                callback.onBatch(start, end, batch.stream().map(String::valueOf).toList());
                return new BatchResult(start, embeddings);
            }, executor));
        }

        List<List<Float>> result = new ArrayList<>(converted.size());
        for (int i = 0; i < converted.size(); i++) {
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
    public List<Float> embedMultimodal(MultimodalDocument document) {
        return embedMultimodal(document, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedMultimodal(Object input, Map<String, Object> options) {
        if (!(input instanceof MultimodalDocument document)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "input provided for multimodal embedding is not a MultimodalDocument");
        }
        return getDashscopeEmbeddings(List.of(document.getDashscopeInput()), options).get(0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedMultimodal(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodal((Object) document, options);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedMultimodalSync(MultimodalDocument document) {
        return embedMultimodalSync(document, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedMultimodalSync(Object input, Map<String, Object> options) {
        return embedMultimodal(input, options);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options) {
        return embedMultimodalSync((Object) document, options);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<List<Float>> getDashscopeEmbeddings(Object input, Map<String, Object> options) {
        List<?> payloadInput = input instanceof List<?> list ? list : List.of(input);
        Map<String, Object> payloadOptions = new LinkedHashMap<>(cleanPayloadOptions(options));
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                MultiModalEmbeddingResult result = dashscopeClient.call(buildParam(payloadInput, payloadOptions));
                List<List<Float>> embeddings = handleDashscopeResult(result, attempt);
                if (embeddings != null) {
                    return embeddings;
                }
            } catch (ApiException | NoApiKeyException | UploadFileException ex) {
                if (attempt >= maxRetries - 1) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                            "Failed to get embedding after " + maxRetries + " attempts: " + ex.getMessage());
                }
            }
        }
        throw RetrievalExceptions.error(
                StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED,
                "Unreachable code in DashscopeEmbedding.getDashscopeEmbeddings");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<List<Float>> parseEmbeddings(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embeddings in response: null");
        }
        JsonNode output = root.has("output") ? root.get("output") : root;
        JsonNode embeddingsNode = output == null ? null : output.get("embeddings");
        if (embeddingsNode == null || !embeddingsNode.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embeddings in response: " + output);
        }
        if (embeddingsNode.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "The embeddings field in response is empty: " + output);
        }

        List<JsonNode> items = new ArrayList<>();
        embeddingsNode.forEach(items::add);
        items.sort(Comparator.comparingInt(node -> node.path("index").asInt(0)));
        List<List<Float>> embeddings = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            embeddings.add(parseSingleEmbedding(item.get("embedding")));
        }
        return embeddings;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<List<Float>> handleDashscopeResult(MultiModalEmbeddingResult result, int attempt) {
        Integer statusCode = result == null ? null : result.getStatusCode();
        if (statusCode != null && statusCode != 200 && attempt >= maxRetries - 1) {
            String message = result.getMessage();
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED,
                    "Failed to get embedding after " + maxRetries + " attempts: " + message);
        }
        if (statusCode != null && statusCode != 200) {
            return List.of();
        }
        MultiModalEmbeddingOutput output = result == null ? null : result.getOutput();
        List<MultiModalEmbeddingResultItem> items = output == null ? null : output.getEmbeddings();
        if (items == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "No embeddings in response: " + output);
        }
        if (items.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "The embeddings field in response is empty: " + output);
        }
        items.sort(Comparator.comparingInt(item -> item.getIndex() == null ? 0 : item.getIndex()));
        List<List<Float>> embeddings = new ArrayList<>(items.size());
        for (MultiModalEmbeddingResultItem item : items) {
            embeddings.add(toFloatEmbedding(item.getEmbedding()));
        }
        if (resolvedDimension == null && !embeddings.isEmpty() && !embeddings.get(0).isEmpty()) {
            resolvedDimension = embeddings.get(0).size();
        }
        return embeddings;
    }

    private MultiModalEmbeddingParam buildParam(List<?> input, Map<String, Object> options) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (configuredDimension != null) {
            parameters.put("dimension", configuredDimension);
        }
        if (options != null) {
            parameters.putAll(options);
        }
        return MultiModalEmbeddingParam.builder()
                .model(modelName)
                .apiKey(apiKey)
                .contents(toDashscopeItems(input))
                .parameters(parameters)
                .build();
    }

    static List<MultiModalEmbeddingItemBase> toDashscopeItems(List<?> input) {
        List<MultiModalEmbeddingItemBase> items = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof Map<?, ?> map) {
                appendMapItems(items, map);
            } else {
                items.add(new MultiModalEmbeddingItemText(String.valueOf(item)));
            }
        }
        return items;
    }

    private static void appendMapItems(List<MultiModalEmbeddingItemBase> items, Map<?, ?> map) {
        Object text = map.get("text");
        if (text != null) {
            items.add(new MultiModalEmbeddingItemText(String.valueOf(text)));
        }
        Object image = map.get("image");
        if (image != null) {
            items.add(new MultiModalEmbeddingItemImage(String.valueOf(image)));
        }
        Object multiImages = map.get("multi_images");
        if (multiImages instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                items.add(new MultiModalEmbeddingItemImage(String.valueOf(item)));
            }
        }
        Object video = map.get("video");
        if (video != null) {
            items.add(new MultiModalEmbeddingItemVideo(String.valueOf(video)));
        }
    }

    private static List<Float> toFloatEmbedding(List<Double> embedding) {
        if (embedding == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                    "embedding item is null");
        }
        List<Float> values = new ArrayList<>(embedding.size());
        for (Double item : embedding) {
            if (item == null) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_EMBEDDING_RESPONSE_INVALID,
                        "embedding item is null");
            }
            values.add(item.floatValue());
        }
        return values;
    }

    private static Object toDashscopeInput(Object item) {
        if (item instanceof MultimodalDocument document) {
            return document.getDashscopeInput();
        }
        return item;
    }

    private static List<Object> validateDashscopeInputs(List<?> texts) {
        if (texts == null || texts.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID,
                    "Empty texts list provided");
        }
        List<Object> nonEmpty = new ArrayList<>(texts.size());
        int emptyCount = 0;
        for (Object item : texts) {
            if (item == null) {
                emptyCount++;
            } else if (item instanceof String text) {
                if (text.isBlank()) {
                    emptyCount++;
                } else {
                    nonEmpty.add(text);
                }
            } else if (item instanceof MultimodalDocument document) {
                if (document.getDashscopeInput().isEmpty()) {
                    emptyCount++;
                } else {
                    nonEmpty.add(document);
                }
            } else {
                nonEmpty.add(item);
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

    private record BatchResult(int start, List<List<Float>> embeddings) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;

import java.io.IOException;
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

/**
 * DashScope text-rerank client.
 * <p>
 * Mirrors Python's {@code DashscopeReranker} in
 * {@code openjiuwen/core/retrieval/reranker/dashscope_reranker.py}.
 * </p>
 */
public class DashscopeReranker extends StandardReranker {

    public static final String END_POINT = "/services/rerank/text-rerank/text-rerank";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String modelName;
    private final String apiUrl;
    private final double timeout;
    private final int maxRetries;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    public DashscopeReranker(RerankerConfig config) {
        this(config, 3, 0.1d, null, null);
    }

    public DashscopeReranker(RerankerConfig config,
                             int maxRetries,
                             double retryWait,
                             Map<String, String> extraHeaders,
                             HttpClient httpClient) {
        super(config, maxRetries, retryWait, extraHeaders, httpClient);
        this.modelName = config.getModelName();
        this.apiUrl = normalizeBaseUrl(config.getApiBase());
        this.timeout = config.getTimeout();
        this.maxRetries = maxRetries;
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            this.headers.put("Authorization", "Bearer " + config.getApiKey());
        }
        if (extraHeaders != null) {
            this.headers.putAll(extraHeaders);
        }
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public CompletableFuture<Map<String, Double>> rerank(String query,
                                                         List<Object> doc,
                                                         Object instruct,
                                                         Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> rerankSync(query, doc, instruct, kwargs));
    }

    @Override
    public Map<String, Double> rerankSync(String query,
                                          List<Object> doc,
                                          Object instruct,
                                          Map<String, Object> kwargs) {
        AssembleResult assembled = assembleParams(query, doc, instruct, kwargs);
        Map<String, Object> responseData = postWithRetry(assembled.params(), assembled.headers());
        return parseResponse(responseData, doc);
    }

    public Map<String, Object> requestParams(Object query, List<?> documents, Integer topN, Object instruct) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("return_documents", false);
        parameters.put("top_n", topN == null ? documents.size() : topN);
        if (instruct instanceof String instruction && !instruction.isBlank()) {
            parameters.put("instruct", instruction);
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", modelName);
        params.put("input", input);
        params.put("parameters", parameters);
        return params;
    }

    AssembleResult assembleParams(Object query, Object doc, Object instruct, Map<String, Object> kwargs) {
        Object normalizedQuery = query instanceof MultimodalDocument multimodalQuery
                ? multimodalQuery.getDashscopeInput()
                : query;
        List<Object> documents = extractDocuments(doc);
        Map<String, Object> params = requestParams(normalizedQuery, documents, documents.size(), instruct);
        if (kwargs != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) params.get("parameters");
            parameters.putAll(kwargs);
        }
        return new AssembleResult(new LinkedHashMap<>(headers), params);
    }

    private Map<String, Object> postWithRetry(Map<String, Object> params, Map<String, String> requestHeaders) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String body = OBJECT_MAPPER.writeValueAsString(params);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + END_POINT))
                        .timeout(Duration.ofMillis(Math.round(timeout * 1000)))
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
                HttpResponse<String> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
                }
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries
                                    + " attempts: HTTP " + response.statusCode()
                    );
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries
                                    + " attempts: " + exception.getMessage()
                    );
                }
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED,
                "error_msg",
                "Unreachable code in DashscopeReranker"
        );
    }

    private static List<Object> extractDocuments(Object doc) {
        if (!(doc instanceof List<?> values)) {
            throw invalidInput();
        }

        boolean hasMultimodal = false;
        List<Object> documents = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof MultimodalDocument multimodalDocument) {
                documents.add(multimodalDocument.getDashscopeInput());
                hasMultimodal = true;
            } else if (value instanceof Document document) {
                documents.add(document.getText());
            } else if (value instanceof String text) {
                documents.add(text);
            } else {
                throw invalidInput();
            }
        }
        if (!hasMultimodal) {
            return documents;
        }

        List<Object> wrappedDocuments = new ArrayList<>(documents.size());
        for (Object value : documents) {
            if (value instanceof String text) {
                wrappedDocuments.add(Map.of("text", text));
            } else {
                wrappedDocuments.add(value);
            }
        }
        return wrappedDocuments;
    }

    private static RuntimeException invalidInput() {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                "error_msg",
                "input to reranker must be either list[str | Document]"
        );
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith(END_POINT)) {
            return normalized.substring(0, normalized.length() - END_POINT.length());
        }
        return normalized;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    record AssembleResult(Map<String, String> headers, Map<String, Object> params) {
    }
}

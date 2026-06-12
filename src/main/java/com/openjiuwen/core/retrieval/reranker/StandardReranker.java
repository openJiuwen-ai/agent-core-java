/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
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

/**
 * Standard reranker client for vLLM-like services.
 * <p>
 * Mirrors Python's {@code StandardReranker} in
 * {@code openjiuwen/core/retrieval/reranker/standard_reranker.py}.
 * </p>
 */
public class StandardReranker extends Reranker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;

    private static final String ENDPOINT = "/rerank";
    private static final String QUERY_TEMPLATE = "<Instruct>: %s\n<Query>: %s\n";
    private static final String DEFAULT_INSTRUCT =
            "Given a search query, retrieve relevant candidates that answer the query.";

    private final RerankerConfig config;
    private final String modelName;
    private final String apiKey;
    private final String apiUrl;
    private final double timeout;
    private final int maxRetries;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    public StandardReranker(RerankerConfig config) {
        this(config, 3, 0.1d, null, null);
    }

    public StandardReranker(RerankerConfig config,
                            int maxRetries,
                            double retryWait,
                            Map<String, String> extraHeaders,
                            HttpClient httpClient) {
        this.config = config;
        this.modelName = config.getModelName();
        this.apiKey = config.getApiKey();
        this.apiUrl = normalizeBaseUrl(config.getApiBase());
        this.timeout = config.getTimeout();
        this.maxRetries = maxRetries;
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (this.apiKey != null && !this.apiKey.isBlank()) {
            this.headers.put("Authorization", "Bearer " + this.apiKey);
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
        RequestPayload requestPayload = assembleParams(query, doc, instruct, kwargs);
        Object response = postWithRetry(requestPayload.params(), requestPayload.headers());
        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) response;
        return parseResponse(responseData, doc);
    }

    @Override
    protected Map<String, Double> parseResponse(Map<String, Object> responseData, List<Object> doc) {
        List<String> docIds = new ArrayList<>(doc.size());
        for (Object value : doc) {
            if (value instanceof String text) {
                docIds.add(text);
            } else if (value instanceof Document document) {
                docIds.add(document.getId());
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                        "error_msg",
                        "input to reranker must be either list[str | Document]"
                );
            }
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (String docId : docIds) {
            result.put(docId, 0.0d);
        }

        Object resultsObject = responseData.get("output");
        if (resultsObject instanceof Map<?, ?> outputMap && outputMap.get("results") instanceof List<?> outputResults) {
            applyScores(outputResults, docIds, result);
            return result;
        }
        if (responseData.get("results") instanceof List<?> topLevelResults) {
            applyScores(topLevelResults, docIds, result);
            return result;
        }

        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                "error_msg",
                "Reranker response missing results field"
        );
    }

    private static void applyScores(List<?> results, List<String> docIds, Map<String, Double> result) {
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object indexValue = map.get("index");
            int index = indexValue instanceof Number number ? number.intValue() : -1;
            if (index < 0 || index >= docIds.size()) {
                continue;
            }
            Object scoreValue = map.get("relevance_score");
            double score = scoreValue instanceof Number number ? number.doubleValue() : 0.0d;
            result.put(docIds.get(index), score);
        }
    }

    private RequestPayload assembleParams(String query,
                                          List<Object> doc,
                                          Object instruct,
                                          Map<String, Object> kwargs) {
        List<String> documents = extractDocuments(doc);
        Map<String, Object> params = requestParams(query, documents, instruct, kwargs);
        return new RequestPayload(headers, params);
    }

    private Map<String, Object> requestParams(String query,
                                              List<String> documents,
                                              Object instruct,
                                              Map<String, Object> kwargs) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", modelName);
        params.put("return_documents", false);
        params.put("query", buildQuery(query, instruct));
        params.put("documents", documents);
        params.put("top_n", documents.size());
        if (config.getExtraBody() != null) {
            params.putAll(config.getExtraBody());
        }
        if (kwargs != null) {
            params.putAll(kwargs);
        }
        return params;
    }

    private List<String> extractDocuments(List<Object> doc) {
        if (doc == null || doc.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "error_msg",
                    "input to reranker must be either list[str | Document]"
            );
        }
        List<String> documents = new ArrayList<>(doc.size());
        for (Object value : doc) {
            if (value instanceof String text) {
                documents.add(text);
            } else if (value instanceof Document document) {
                if (value instanceof MultimodalDocument) {
                    LOGGER.warning("Reranker received a multimodal reranking request, not supported in current Java port");
                }
                documents.add(document.getText());
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                        "error_msg",
                        "input to reranker must be either list[str | Document]"
                );
            }
        }
        return documents;
    }

    private Object postWithRetry(Map<String, Object> params, Map<String, String> headers) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String body = OBJECT_MAPPER.writeValueAsString(params);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + ENDPOINT))
                        .timeout(Duration.ofMillis(Math.round(timeout * 1000)))
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
                HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return OBJECT_MAPPER.readValue(response.body(), Map.class);
                }
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries + " attempts: HTTP " + response.statusCode()
                    );
                }
            } catch (Exception exception) {
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries + " attempts: " + exception.getMessage()
                    );
                }
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED,
                "error_msg",
                "Unreachable code in StandardReranker"
        );
    }

    private static String buildQuery(String query, Object instruct) {
        if (Boolean.TRUE.equals(instruct)) {
            return QUERY_TEMPLATE.formatted(DEFAULT_INSTRUCT, query);
        }
        if (instruct instanceof String text && !text.isBlank()) {
            return QUERY_TEMPLATE.formatted(text, query);
        }
        return query;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith(ENDPOINT)) {
            return normalized.substring(0, normalized.length() - ENDPOINT.length());
        }
        return normalized;
    }

    private record RequestPayload(Map<String, String> headers, Map<String, Object> params) {
    }
}

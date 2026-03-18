/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.utils.ApiRequestUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote reranker implementation aligned with Python's StandardReranker behavior.
 */
public class StandardReranker implements Reranker {

    protected static final String ENDPOINT = "/rerank";
    protected static final String QUERY_TEMPLATE = "<Instruct>: %s\n<Query>: %s\n";
    protected static final String DEFAULT_INSTRUCT =
            "Given a search query, retrieve relevant candidates that answer the query.";

    protected final RerankerConfig config;
    protected final String modelName;
    protected final String apiKey;
    protected final String apiUrl;
    protected final int maxRetries;
    protected final Map<String, String> headers;
    protected final HttpClient httpClient;

    public StandardReranker(RerankerConfig config) {
        this(config, 3, null, null);
    }

    public StandardReranker(RerankerConfig config,
                            int maxRetries,
                            Map<String, String> extraHeaders,
                            HttpClient httpClient) {
        if (config == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID, "RerankerConfig is required");
        }
        this.config = config;
        this.modelName = config.getModelName();
        this.apiKey = config.getApiKey();
        this.apiUrl = normalizeBaseUrl(config.getApiBase(), ENDPOINT);
        this.maxRetries = Math.max(1, maxRetries);
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            this.headers.put("Authorization", "Bearer " + apiKey);
        }
        if (extraHeaders != null) {
            this.headers.putAll(extraHeaders);
        }
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public Map<String, Double> rerankScores(String query, List<?> documents) {
        return rerankScores(query, documents, Boolean.TRUE, Map.of());
    }

    @Override
    public Map<String, Double> rerankScores(String query,
                                            List<?> documents,
                                            Object instruct,
                                            Map<String, Object> options) {
        CandidateBatch batch = prepareCandidates(documents);
        List<Double> orderedScores = rerankOrderedScores(query, batch.texts(), instruct, options);
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < batch.ids().size(); i++) {
            result.put(batch.ids().get(i), orderedScores.get(i));
        }
        return result;
    }

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Double> scores = rerankOrderedScores(query, candidates.stream().map(RetrievalResult::getText).toList(), Boolean.TRUE, Map.of());
        List<RetrievalResult> reranked = new ArrayList<>(candidates);
        for (int i = 0; i < reranked.size(); i++) {
            reranked.get(i).setScore(scores.get(i));
        }
        reranked.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
        return reranked.size() <= topK ? reranked : new ArrayList<>(reranked.subList(0, topK));
    }

    protected List<Double> rerankOrderedScores(String query,
                                               List<String> documents,
                                               Object instruct,
                                               Map<String, Object> options) {
        JsonNode response = ApiRequestUtils.postJsonWithRetry(
                httpClient,
                apiUrl + ENDPOINT,
                buildRequestPayload(query, documents, instruct, options),
                headers,
                Duration.ofMillis(Math.round(config.getTimeout() * 1000)),
                maxRetries,
                StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                "Reranker");
        return parseOrderedScores(response, documents.size());
    }

    protected Map<String, Object> buildRequestPayload(String query,
                                                      List<String> documents,
                                                      Object instruct,
                                                      Map<String, Object> options) {
        String finalQuery = buildQuery(query, instruct);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("return_documents", false);
        payload.put("query", finalQuery);
        payload.put("documents", documents);
        payload.put("top_n", documents.size());
        payload.putAll(config.getExtraBody());
        if (options != null) {
            payload.putAll(options);
        }
        return payload;
    }

    protected List<Double> parseOrderedScores(JsonNode response, int documentCount) {
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < documentCount; i++) {
            scores.add(0.0);
        }
        JsonNode results = response.path("output").isObject() ? response.path("output").path("results") : response.path("results");
        if (!results.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                    "Reranker response missing results field");
        }
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index >= 0 && index < scores.size()) {
                scores.set(index, result.path("relevance_score").asDouble(0.0));
            }
        }
        return scores;
    }

    protected static String buildQuery(String query, Object instruct) {
        if (Boolean.TRUE.equals(instruct)) {
            return QUERY_TEMPLATE.formatted(DEFAULT_INSTRUCT, query);
        }
        if (instruct instanceof String instruction && !instruction.isBlank()) {
            return QUERY_TEMPLATE.formatted(instruction, query);
        }
        return query;
    }

    protected static CandidateBatch prepareCandidates(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "input to reranker must be a non-empty list");
        }
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Object document : documents) {
            if (document instanceof String text) {
                ids.add(text);
                texts.add(text);
            } else if (document instanceof Document doc) {
                ids.add(doc.getId());
                texts.add(doc.getText());
            } else if (document instanceof RetrievalResult result) {
                ids.add(candidateId(result));
                texts.add(result.getText());
            } else {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                        "input to reranker must be either list[str | Document | RetrievalResult]");
            }
        }
        return new CandidateBatch(ids, texts);
    }

    protected static String candidateId(RetrievalResult result) {
        if (result.getChunkId() != null && !result.getChunkId().isBlank()) {
            return result.getChunkId();
        }
        if (result.getDocId() != null && !result.getDocId().isBlank()) {
            return result.getDocId();
        }
        return Integer.toHexString(result.getText().hashCode());
    }

    protected static String normalizeBaseUrl(String baseUrl, String endpoint) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith(endpoint)) {
            normalized = normalized.substring(0, normalized.length() - endpoint.length());
        }
        return normalized;
    }

    protected record CandidateBatch(List<String> ids, List<String> texts) {
    }
}

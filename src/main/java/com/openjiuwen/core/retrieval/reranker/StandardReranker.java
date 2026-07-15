/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * 
 * @since 0.1.7
 */
public class StandardReranker implements Reranker {
    /**
     * ENDPOINT.
     * 
     * @since 0.1.7
     */
    protected static final String ENDPOINT = "/rerank";

    /**
     * QUERY_TEMPLATE.
     * 
     * @since 0.1.7
     */
    protected static final String QUERY_TEMPLATE = "<Instruct>: %s\n<Query>: %s\n";

    /**
     * DEFAULT_INSTRUCT.
     * 
     * @since 0.1.7
     */
    protected static final String DEFAULT_INSTRUCT =
        "Given a search query, retrieve relevant candidates that answer the query.";

    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final RerankerConfig config;

    /**
     * modelName.
     * 
     * @since 0.1.7
     */
    protected final String modelName;

    /**
     * apiKey.
     * 
     * @since 0.1.7
     */
    protected final String apiKey;

    /**
     * apiUrl.
     * 
     * @since 0.1.7
     */
    protected final String apiUrl;

    /**
     * maxRetries.
     * 
     * @since 0.1.7
     */
    protected final int maxRetries;

    /**
     * headers.
     * 
     * @since 0.1.7
     */
    protected final Map<String, String> headers;

    /**
     * httpClient.
     * 
     * @since 0.1.7
     */
    protected final HttpClient httpClient;

    /**
     * StandardReranker.
     * 
     * @param config config
     * @since 0.1.7
     */
    public StandardReranker(RerankerConfig config) {
        this(config, 3, null, null);
    }

    /**
     * StandardReranker.
     * 
     * @param config config
     * @param maxRetries maxRetries
     * @param extraHeaders extraHeaders
     * @param httpClient httpClient
     * @since 0.1.7
     */
    public StandardReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders,
            HttpClient httpClient) {
        if (config == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID, "RerankerConfig is required");
        }
        this.config = config;
        this.modelName = config.getModelName();
        this.apiKey = config.getApiKey();
        this.apiUrl = normalizeBaseUrl(config.getApiBase(), endpoint());
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

    /**
     * rerankScores.
     * 
     * @param query query
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Double> rerankScores(String query, List<?> documents) {
        return rerankScores(query, documents, Boolean.TRUE, Map.of());
    }

    /**
     * rerankScores.
     * 
     * @param query query
     * @param documents documents
     * @param instruct instruct
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Double> rerankScores(String query, List<?> documents, Object instruct,
            Map<String, Object> options) {
        CandidateBatch batch = prepareCandidates(documents);
        List<Double> orderedScores = rerankOrderedScores(query, batch.texts(), instruct, options);
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < batch.ids().size(); i++) {
            result.put(batch.ids().get(i), orderedScores.get(i));
        }
        return result;
    }

    /**
     * rerank.
     * 
     * @param query query
     * @param candidates candidates
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Double> scores = rerankOrderedScores(query, candidates.stream().map(RetrievalResult::getText).toList(),
                Boolean.TRUE, Map.of());
        List<RetrievalResult> reranked = new ArrayList<>(candidates);
        for (int i = 0; i < reranked.size(); i++) {
            reranked.get(i).setScore(scores.get(i));
        }
        reranked.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
        return reranked.size() <= topK ? reranked : new ArrayList<>(reranked.subList(0, topK));
    }

    /**
     * rerankOrderedScores.
     * 
     * @param query query
     * @param documents documents
     * @param instruct instruct
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    protected List<Double> rerankOrderedScores(String query, List<String> documents, Object instruct,
            Map<String, Object> options) {
        JsonNode response = ApiRequestUtils.postJsonWithRetry(httpClient, apiUrl + endpoint(),
                buildRequestPayload(query, documents, instruct, options), headers,
                Duration.ofMillis(Math.round(config.getTimeout() * 1000)), maxRetries,
                StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED, "Reranker");
        return parseOrderedScores(response, documents.size());
    }

    /**
     * buildRequestPayload.
     * 
     * @param query query
     * @param documents documents
     * @param instruct instruct
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    protected Map<String, Object> buildRequestPayload(String query, List<String> documents, Object instruct,
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

    /**
     * endpoint.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected String endpoint() {
        return ENDPOINT;
    }

    /**
     * getModelName.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected String getModelName() {
        return modelName;
    }

    /**
     * parseOrderedScores.
     * 
     * @param response response
     * @param documentCount documentCount
     * @return the result
     * @since 0.1.7
     */
    protected List<Double> parseOrderedScores(JsonNode response, int documentCount) {
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < documentCount; i++) {
            scores.add(0.0);
        }
        JsonNode results =
            response.path("output").isObject() ? response.path("output").path("results") : response.path("results");
        if (!results.isArray()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
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

    /**
     * buildQuery.
     * 
     * @param query query
     * @param instruct instruct
     * @return the result
     * @since 0.1.7
     */
    protected static String buildQuery(String query, Object instruct) {
        if (Boolean.TRUE.equals(instruct)) {
            return QUERY_TEMPLATE.formatted(DEFAULT_INSTRUCT, query);
        }
        if (instruct instanceof String instruction && !instruction.isBlank()) {
            return QUERY_TEMPLATE.formatted(instruction, query);
        }
        return query;
    }

    /**
     * prepareCandidates.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    protected static CandidateBatch prepareCandidates(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
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
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                        "input to reranker must be either list[str | Document | RetrievalResult]");
            }
        }
        return new CandidateBatch(ids, texts);
    }

    /**
     * candidateId.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    protected static String candidateId(RetrievalResult result) {
        if (result.getChunkId() != null && !result.getChunkId().isBlank()) {
            return result.getChunkId();
        }
        if (result.getDocId() != null && !result.getDocId().isBlank()) {
            return result.getDocId();
        }
        return Integer.toHexString(result.getText().hashCode());
    }

    /**
     * normalizeBaseUrl.
     * 
     * @param baseUrl baseUrl
     * @param endpoint endpoint
     * @return the result
     * @since 0.1.7
     */
    protected static String normalizeBaseUrl(String baseUrl, String endpoint) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith(endpoint)) {
            normalized = normalized.substring(0, normalized.length() - endpoint.length());
        }
        return normalized;
    }

    /**
     * CandidateBatch.
     * 
     * @since 0.1.7
     */
    protected record CandidateBatch(List<String> ids, List<String> texts) {
    }
}

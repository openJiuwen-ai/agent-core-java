/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.utils.ApiRequestUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashscope reranker implementation aligned with Python's DashscopeReranker behavior.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval.reranker.dashscope_reranker.DashscopeReranker}.</p>
 */
public class DashscopeReranker extends StandardReranker {

    public static final String END_POINT = "/services/rerank/text-rerank/text-rerank";

    public DashscopeReranker(RerankerConfig config) {
        super(config);
    }

    public DashscopeReranker(RerankerConfig config, HttpClient httpClient) {
        super(config, 3, null, httpClient);
    }

    public DashscopeReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient) {
        super(config, maxRetries, extraHeaders, httpClient);
    }

    @Override
    protected String endpoint() {
        return END_POINT;
    }

    public Map<String, Object> requestParams(Object query, List<?> documents, Integer topN, Object instruct) {
        int actualTopN = topN != null ? topN : documents.size();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("return_documents", false);
        parameters.put("top_n", actualTopN);
        if (instruct instanceof String instruction && !instruction.isBlank()) {
            parameters.put("instruct", instruction);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", modelName);
        params.put("input", Map.of("query", query, "documents", documents));
        params.put("parameters", parameters);
        return params;
    }

    public AssembleResult assembleParams(Object query, List<?> documents, Object instruct, Map<String, Object> kwargs) {
        Object finalQuery;
        if (query instanceof MultimodalDocument mmDoc) {
            finalQuery = mmDoc.getDashscopeInput();
        } else {
            finalQuery = query;
        }
        List<Object> processedDocuments = null;
        if (documents != null) {
            boolean hasMultimodal = false;
            processedDocuments = new ArrayList<>();
            for (Object doc : documents) {
                if (doc instanceof MultimodalDocument mmDoc) {
                    processedDocuments.add(mmDoc.getDashscopeInput());
                    hasMultimodal = true;
                } else if (doc instanceof Document standardDoc) {
                    processedDocuments.add(standardDoc.getText());
                } else if (doc instanceof String str) {
                    processedDocuments.add(str);
                }
            }
            if (hasMultimodal) {
                List<Object> wrappedDocuments = new ArrayList<>();
                for (Object doc : processedDocuments) {
                    if (doc instanceof String str) {
                        wrappedDocuments.add(Map.of("text", str));
                    } else {
                        wrappedDocuments.add(doc);
                    }
                }
                processedDocuments = wrappedDocuments;
            }
        }
        if (processedDocuments == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "input to reranker must be either list[str | Document]");
        }
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        Map<String, Object> params = requestParams(
                finalQuery instanceof String ? (Object) finalQuery : finalQuery,
                processedDocuments,
                processedDocuments.size(),
                instruct
        );
        if (kwargs != null) {
            params.get("parameters");
            Map<String, Object> parameters = (Map<String, Object>) params.get("parameters");
            parameters.putAll(kwargs);
        }
        return new AssembleResult(requestHeaders, params);
    }

    @Override
    protected List<Double> rerankOrderedScores(String query,
                                                List<String> documents,
                                                Object instruct,
                                                Map<String, Object> options) {
        AssembleResult assembled = assembleParams(query, documents, instruct, options);
        JsonNode response = ApiRequestUtils.postJsonWithRetry(
                httpClient,
                apiUrl + endpoint(),
                assembled.params(),
                assembled.headers(),
                Duration.ofMillis(Math.round(config.getTimeout() * 1000)),
                maxRetries,
                StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                "DashscopeReranker");
        return parseOrderedScores(response, documents.size());
    }

    public Map<String, Double> rerankScoresWithDocuments(Object query, List<?> documents, Object instruct, Map<String, Object> kwargs) {
        AssembleResult assembled = assembleParams(query, documents, instruct, kwargs);
        List<?> processedDocs = (List<?>) assembled.params().get("input");
        processedDocs = (List<?>) ((Map<String, Object>) processedDocs).get("documents");
        List<String> docTexts = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        for (Object doc : documents) {
            if (doc instanceof String str) {
                docIds.add(str);
                docTexts.add(str);
            } else if (doc instanceof Document standardDoc) {
                docIds.add(standardDoc.getId());
                docTexts.add(standardDoc.getText());
            } else if (doc instanceof MultimodalDocument) {
                docIds.add(((MultimodalDocument) doc).getId());
                docTexts.add(((MultimodalDocument) doc).getText());
            }
        }
        JsonNode response = ApiRequestUtils.postJsonWithRetry(
                httpClient,
                apiUrl + endpoint(),
                assembled.params(),
                assembled.headers(),
                Duration.ofMillis(Math.round(config.getTimeout() * 1000)),
                maxRetries,
                StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                "DashscopeReranker");
        List<Double> scores = parseOrderedScores(response, docTexts.size());
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < docIds.size(); i++) {
            result.put(docIds.get(i), scores.get(i));
        }
        return result;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    protected record AssembleResult(Map<String, String> headers, Map<String, Object> params) {
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * Hybrid retriever combining sparse and dense retrieval.
 */
public class HybridRetriever extends AbstractStoreBackedRetriever {

    private final double alpha;

    public HybridRetriever(VectorStore vectorStore, Embedding embedModel) {
        this(vectorStore, embedModel, 0.5);
    }

    public HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha) {
        super(vectorStore, embedModel);
        this.alpha = alpha;
    }

    @Override
    public List<RetrievalResult> retrieve(String query,
                                          int topK,
                                          Double scoreThreshold,
                                          String mode,
                                          Map<String, Object> options) {
        if (scoreThreshold != null && !"vector".equals(mode)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID,
                    "score_threshold is only supported when mode='vector'");
        }
        List<SearchResult> searchResults;
        Map<String, Object> filters = VectorRetriever.castMap(options == null ? null : options.get("filters"));
        if ("hybrid".equals(mode)) {
            double alphaValue = resolveAlpha(options);
            List<Float> queryVector = embedModel == null ? null : embedModel.embedQuery(query);
            searchResults = vectorStore.hybridSearch(query, queryVector, topK, alphaValue, filters, options);
        } else if ("vector".equals(mode)) {
            if (embedModel == null) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                        "embed_model is required for vector search");
            }
            searchResults = vectorStore.search(embedModel.embedQuery(query), topK, filters, options);
            if (searchResults.isEmpty()) {
                searchResults = vectorStore.sparseSearch(query, topK, filters, options);
            }
        } else if ("sparse".equals(mode)) {
            searchResults = vectorStore.sparseSearch(query, topK, filters, options);
        } else {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT, "Unsupported mode: " + mode);
        }
        return VectorRetriever.toRetrievalResults(searchResults, "vector".equals(mode) ? scoreThreshold : null);
    }

    @Override
    public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options) {
        Map<String, Object> filters = VectorRetriever.castMap(options == null ? null : options.get("filters"));
        if ("hybrid".equals(mode)) {
            return vectorStore.hybridSearch(
                    query,
                    embedModel == null ? null : embedModel.embedQuery(query),
                    topK,
                    resolveAlpha(options),
                    filters,
                    options);
        }
        if ("vector".equals(mode)) {
            if (embedModel == null) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                        "embed_model is required for vector search");
            }
            List<SearchResult> results = vectorStore.search(embedModel.embedQuery(query), topK, filters, options);
            return results.isEmpty() ? vectorStore.sparseSearch(query, topK, filters, options) : results;
        }
        if ("sparse".equals(mode)) {
            return vectorStore.sparseSearch(query, topK, filters, options);
        }
        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT, "Unsupported mode: " + mode);
    }

    @Override
    public boolean supportsMode(String mode) {
        return "hybrid".equals(mode) || "vector".equals(mode) || "sparse".equals(mode);
    }

    private double resolveAlpha(Map<String, Object> options) {
        if (options != null && options.get("alpha") instanceof Number number) {
            return number.doubleValue();
        }
        return alpha;
    }
}

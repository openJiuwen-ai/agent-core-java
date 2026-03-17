/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure vector retriever.
 */
public class VectorRetriever extends AbstractStoreBackedRetriever {

    public VectorRetriever(VectorStore vectorStore, Embedding embedModel) {
        super(vectorStore, embedModel);
    }

    @Override
    public List<RetrievalResult> retrieve(String query,
                                          int topK,
                                          Double scoreThreshold,
                                          String mode,
                                          Map<String, Object> options) {
        if (!"vector".equals(mode)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                    "VectorRetriever only supports 'vector' mode, got " + mode);
        }
        if (embedModel == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                    "embed_model is required for vector search");
        }
        List<Float> queryVector = embedModel.embedQuery(query);
        Map<String, Object> filters = options == null ? null : castMap(options.get("filters"));
        List<SearchResult> searchResults = vectorStore.search(queryVector, topK, filters, options);
        if (searchResults.isEmpty()) {
            searchResults = vectorStore.sparseSearch(query, topK, filters, options);
        }
        return toRetrievalResults(searchResults, scoreThreshold);
    }

    @Override
    public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options) {
        if (embedModel == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                    "embed_model is required for vector search");
        }
        Map<String, Object> filters = options == null ? null : castMap(options.get("filters"));
        List<SearchResult> searchResults = vectorStore.search(embedModel.embedQuery(query), topK, filters, options);
        if (searchResults.isEmpty()) {
            searchResults = vectorStore.sparseSearch(query, topK, filters, options);
        }
        return searchResults;
    }

    @Override
    public boolean supportsMode(String mode) {
        return "vector".equals(mode);
    }

    static List<RetrievalResult> toRetrievalResults(List<SearchResult> searchResults, Double scoreThreshold) {
        List<RetrievalResult> results = new ArrayList<>();
        for (SearchResult result : searchResults) {
            if (scoreThreshold != null && result.getScore() < scoreThreshold) {
                continue;
            }
            Map<String, Object> metadata = result.getMetadata();
            results.add(new RetrievalResult(
                    result.getText(),
                    result.getScore(),
                    metadata,
                    metadata == null ? null : stringValue(metadata.get("doc_id")),
                    metadata == null ? null : stringValue(metadata.get("chunk_id"))));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

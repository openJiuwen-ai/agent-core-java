/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sparse / BM25-like retriever.
 * 
 * @since 0.1.7
 */
public class SparseRetriever extends AbstractStoreBackedRetriever {
    /**
     * SparseRetriever.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public SparseRetriever(VectorStore vectorStore) {
        super(vectorStore, null);
    }

    /**
     * retrieve.
     * 
     * @param query query
     * @param topK topK
     * @param scoreThreshold scoreThreshold
     * @param mode mode
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode,
            Map<String, Object> options) {
        if (!"sparse".equals(mode)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                    "SparseRetriever only supports 'sparse' mode, got " + mode);
        }
        List<SearchResult> searchResults = vectorStore.sparseSearch(query, topK,
                VectorRetriever.castMap(options == null ? null : options.get("filters")), options);
        List<RetrievalResult> results = new ArrayList<>();
        for (SearchResult result : searchResults) {
            Map<String, Object> metadata = result.getMetadata();
            results.add(new RetrievalResult(result.getText(), result.getScore(), metadata,
                    metadata == null ? null : VectorRetriever.stringValue(metadata.get("doc_id")), result.getId()));
        }
        return results;
    }

    /**
     * retrieveSearchResults.
     * 
     * @param query query
     * @param topK topK
     * @param mode mode
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options) {
        if (!"sparse".equals(mode)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                    "SparseRetriever only supports 'sparse' mode, got " + mode);
        }
        return vectorStore.sparseSearch(query, topK,
                VectorRetriever.castMap(options == null ? null : options.get("filters")), options);
    }

    /**
     * supportsMode.
     * 
     * @param mode mode
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean supportsMode(String mode) {
        return "sparse".equals(mode);
    }
}

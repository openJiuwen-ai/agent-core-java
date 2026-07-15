/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * Unified retriever abstraction.
 * 
 * @since 0.1.7
 */
public interface Retriever extends AutoCloseable {
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
    List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode,
            Map<String, Object> options);

    /**
     * batchRetrieve.
     * 
     * @param queries queries
     * @param topK topK
     * @param mode mode
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options);

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
    default List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options) {
        List<RetrievalResult> retrievalResults = retrieve(query, topK, null, mode, options);
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return List.of();
        }
        return retrievalResults.stream().map(result -> {
            String id = result.getChunkId();
            if (id == null || id.isBlank()) {
                id = result.getDocId();
            }
            if (id == null || id.isBlank()) {
                id = Integer.toHexString(result.getText().hashCode());
            }
            return new SearchResult(id, result.getText(), result.getScore(), result.getMetadata());
        }).toList();
    }

    /**
     * retrieve.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    default List<RetrievalResult> retrieve(String query) {
        return retrieve(query, 5, null, "hybrid", Map.of());
    }

    /**
     * retrieve.
     * 
     * @param query query
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    default List<RetrievalResult> retrieve(String query, int topK) {
        return retrieve(query, topK, null, "hybrid", Map.of());
    }

    /**
     * supportsMode.
     * 
     * @param mode mode
     * @return the result
     * @since 0.1.7
     */
    default boolean supportsMode(String mode) {
        return true;
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    default String getIndexType() {
        return "hybrid";
    }

    @Override
    /**
     * close.
     * 
     * @since 0.1.7
     */
    default void close() {
    }
}

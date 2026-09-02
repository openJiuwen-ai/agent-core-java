/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker abstraction.
 * <p>
 * Mirrors the Python {@code Reranker} contract which exposes both a
 * document→score mapping ({@link #rerankScores}) and a ranked list
 * ({@link #rerank}).
 * </p>
 * 
 * @since 0.1.7
 */
public interface Reranker {
    /**
     * rerank.
     * 
     * @param query query
     * @param candidates candidates
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK);

    /**
     * Rerank documents and return a mapping from document identifier to relevance score.
     * Corresponds to Python {@code Reranker.rerank / rerank_sync} returning {@code dict[str, float]}.
     * <p>
     * Default implementation delegates to {@link #rerank} and converts.
     * </p>
     * 
     * @param query query
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    default Map<String, Double> rerankScores(String query, List<?> documents) {
        return rerankScores(query, documents, Boolean.TRUE, Map.of());
    }

    /**
     * Rerank documents and return a mapping from document identifier to relevance score.
     * 
     * @param query query string
     * @param documents list of String, Document, or RetrievalResult
     * @param instruct whether to provide instruction (true=default instruct, String=custom)
     * @param options extra arguments
     * @return the result
     * @since 0.1.7
     */
    default Map<String, Double> rerankScores(String query, List<?> documents, Object instruct,
            Map<String, Object> options) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("input to reranker must be a non-empty list");
        }
        List<RetrievalResult> candidates = new ArrayList<>(documents.size());
        for (Object document : documents) {
            if (document instanceof String text) {
                candidates.add(new RetrievalResult(text, 0.0, Map.of(), text, text));
            } else if (document instanceof Document doc) {
                candidates.add(new RetrievalResult(doc.getText(), 0.0, Map.of(), doc.getId(), doc.getId()));
            } else if (document instanceof RetrievalResult result) {
                candidates.add(result);
            } else {
                throw new IllegalArgumentException(
                        "input to reranker must be either list[str | Document | RetrievalResult]");
            }
        }
        List<RetrievalResult> reranked = rerank(query, candidates, candidates.size());
        Map<String, Double> scores = new LinkedHashMap<>();
        for (RetrievalResult result : reranked) {
            String id = result.getChunkId();
            if (id == null || id.isBlank()) {
                id = result.getDocId();
            }
            if (id == null || id.isBlank()) {
                id = Integer.toHexString(result.getText().hashCode());
            }
            scores.put(id, result.getScore());
        }
        return scores;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

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
        StandardReranker.CandidateBatch batch = StandardReranker.prepareCandidates(documents);
        List<RetrievalResult> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < batch.ids().size(); i++) {
            candidates.add(new RetrievalResult(batch.texts().get(i), 0.0, java.util.Map.of(), batch.ids().get(i),
                    batch.ids().get(i)));
        }
        List<RetrievalResult> reranked = rerank(query, candidates, candidates.size());
        java.util.Map<String, Double> scores = new java.util.LinkedHashMap<>();
        for (RetrievalResult result : reranked) {
            String id = StandardReranker.candidateId(result);
            scores.put(id, result.getScore());
        }
        return scores;
    }
}

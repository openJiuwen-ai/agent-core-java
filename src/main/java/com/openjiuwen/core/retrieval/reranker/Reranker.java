/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.List;
import java.util.Map;

/**
 * Reranker abstraction.
 *
 * <p>Mirrors the Python {@code Reranker} contract which exposes both a
 * document→score mapping ({@link #rerankScores}) and a ranked list
 * ({@link #rerank}).</p>
 */
public interface Reranker {

    /**
     * Rerank candidates and return a ranked list.
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK);

    /**
     * Rerank documents and return a mapping from document identifier to relevance score.
     * Corresponds to Python {@code Reranker.rerank / rerank_sync} returning {@code dict[str, float]}.
     *
     * <p>Default implementation delegates to {@link #rerank} and converts.</p>
     */
    default Map<String, Double> rerankScores(String query, List<?> documents) {
        return rerankScores(query, documents, Boolean.TRUE, Map.of());
    }

    /**
     * Rerank documents and return a mapping from document identifier to relevance score.
     *
     * @param query    query string
     * @param documents list of String, Document, or RetrievalResult
     * @param instruct whether to provide instruction (true=default instruct, String=custom)
     * @param options  extra arguments
     */
    default Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options) {
        // Default: no-op, concrete classes should override
        throw new UnsupportedOperationException("rerankScores not implemented");
    }
}

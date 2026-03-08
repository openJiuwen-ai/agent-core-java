/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexicalRerankerTest {

    @Test
    void rerankSortsByTokenOverlap() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                new RetrievalResult("banana cherry date", 0.0),
                new RetrievalResult("apple banana cherry", 0.0),
                new RetrievalResult("apple apple banana apple cherry", 0.0)));

        List<RetrievalResult> results = reranker.rerank("apple banana", candidates, 3);

        assertEquals(3, results.size());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void rerankEmptyCandidates() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> results = reranker.rerank("test", List.of(), 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void rerankNullCandidates() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> results = reranker.rerank("test", null, 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void rerankRespectsTopK() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                new RetrievalResult("alpha beta", 0.0),
                new RetrievalResult("alpha gamma", 0.0),
                new RetrievalResult("alpha delta", 0.0)));

        List<RetrievalResult> results = reranker.rerank("alpha", candidates, 2);
        assertEquals(2, results.size());
    }

    @Test
    void rerankNoOverlapGivesZeroScore() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                new RetrievalResult("xyz uvw", 0.0)));

        List<RetrievalResult> results = reranker.rerank("abc def", candidates, 5);
        assertEquals(1, results.size());
        assertEquals(0.0, results.get(0).getScore(), 0.001);
    }

    @Test
    void rerankSingleCandidate() {
        LexicalReranker reranker = new LexicalReranker();
        List<RetrievalResult> candidates = new ArrayList<>(List.of(
                new RetrievalResult("hello world", 0.0)));

        List<RetrievalResult> results = reranker.rerank("hello", candidates, 5);
        assertEquals(1, results.size());
        assertTrue(results.get(0).getScore() > 0.0);
    }
}

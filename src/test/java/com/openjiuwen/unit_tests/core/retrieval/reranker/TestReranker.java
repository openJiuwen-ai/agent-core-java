/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.reranker.Reranker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reranker abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/reranker/test_base.py
 */
class TestReranker {

    /**
     * Concrete reranker implementation for testing.
     */
    static class ConcreteReranker implements Reranker {

        @Override
        public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
            return new ArrayList<>(candidates);
        }
    }

    @Test
    void testRerankerInterface() {
        // Test that Reranker interface exists
        assertNotNull(Reranker.class);
    }

    @Test
    void testConcreteReranker() {
        // Test concrete implementation
        ConcreteReranker reranker = new ConcreteReranker();
        List<RetrievalResult> input = List.of(
            new RetrievalResult("doc1", 0.95)
        );
        List<RetrievalResult> result = reranker.rerank("test query", input, 10);
        assertNotNull(result);
    }
}
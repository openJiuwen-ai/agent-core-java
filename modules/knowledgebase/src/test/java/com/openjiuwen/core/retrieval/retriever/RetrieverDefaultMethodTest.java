/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class RetrieverDefaultMethodTest {
    @Test
    void retrieveSearchResultsFallsBackToRetrieve() {
        Retriever retriever = new Retriever() {
            @Override
            public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode,
                    Map<String, Object> options) {
                return List.of(new RetrievalResult("text", 0.9, Map.of("doc_id", "doc-1"), "doc-1", "chunk-1"));
            }

            @Override
            public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode,
                    Map<String, Object> options) {
                return List.of();
            }
        };

        List<SearchResult> results = retriever.retrieveSearchResults("query", 5, "hybrid", Map.of());

        assertEquals(1, results.size());
        assertEquals("chunk-1", results.get(0).getId());
        assertEquals("text", results.get(0).getText());
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieverDefaultMethodTest {

    @Test
    void retrieveSearchResultsFallsBackToRetrieve() {
        Retriever retriever = new Retriever() {
            @Override
            public List<RetrievalResult> retrieve(
                    String query,
                    int topK,
                    Double scoreThreshold,
                    String mode,
                    Map<String, Object> options
            ) {
                return List.of(new RetrievalResult("text", 0.9d, Map.of("doc_id", "doc-1"), "doc-1", "chunk-1"));
            }

            @Override
            public List<List<RetrievalResult>> batchRetrieve(
                    List<String> queries,
                    int topK,
                    String mode,
                    Map<String, Object> options
            ) {
                return List.of();
            }
        };

        List<SearchResult> results = retriever.retrieveSearchResults("query", 5, "hybrid", Map.of());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("chunk-1");
        assertThat(results.get(0).getText()).isEqualTo("text");
    }
}

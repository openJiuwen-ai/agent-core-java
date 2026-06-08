/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RerankerBridgeTest {

    @Test
    void retrievalRerankerBridgeKeepsBaseRerankerContract() {
        Reranker reranker = new Reranker() {
            @Override
            public CompletableFuture<Map<String, Double>> rerank(
                    String query,
                    List<Object> doc,
                    Object instruct,
                    Map<String, Object> kwargs
            ) {
                return CompletableFuture.completedFuture(Map.of("doc", 1.0));
            }

            @Override
            public Map<String, Double> rerankSync(
                    String query,
                    List<Object> doc,
                    Object instruct,
                    Map<String, Object> kwargs
            ) {
                return Map.of("doc", 1.0);
            }
        };

        assertThat(reranker).isInstanceOf(com.openjiuwen.core.foundation.store.base_reranker.Reranker.class);
        assertThat(reranker.rerankSync("q", List.of("doc"), true, Map.of())).containsEntry("doc", 1.0);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingBridgeTest {

    @Test
    void retrievalEmbeddingBridgePreservesBaseEmbeddingContract() {
        Embedding embedding = new Embedding() {
            @Override
            public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(List.of((double) text.length()));
            }

            @Override
            public CompletableFuture<List<List<Double>>> embedDocuments(
                    List<String> texts,
                    Integer batchSize,
                    Map<String, Object> kwargs
            ) {
                return CompletableFuture.completedFuture(
                        texts.stream().map(text -> List.of((double) text.length())).toList()
                );
            }

            @Override
            public int getDimension() {
                return 1;
            }
        };

        assertThat(embedding.embedQuery("hello").join()).containsExactly(5.0d);
        assertThat(embedding.embedDocuments(List.of("a", "bb"), 2).join())
                .containsExactly(List.of(1.0d), List.of(2.0d));
        assertThat(embedding.getDimension()).isEqualTo(1);
    }
}

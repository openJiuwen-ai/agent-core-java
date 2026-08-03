/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code Embedding} in
 * {@code openjiuwen/core/retrieval/embedding/base.py}.
 *
 * <p>Mirrors Python's supplemental coverage in
 * {@code tests/unit_tests/core/retrieval/embedding/test_base.py}.</p>
 */
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

    @Test
    void embedQuery() {
        ConcreteEmbedding model = new ConcreteEmbedding();

        List<Double> embedding = model.embedQuery("test query").join();

        assertThat(embedding).hasSize(384);
        assertThat(embedding).allSatisfy(value -> assertThat(value).isInstanceOf(Double.class));
    }

    @Test
    void embedDocuments() {
        ConcreteEmbedding model = new ConcreteEmbedding();
        List<String> texts = List.of("text 1", "text 2", "text 3");

        List<List<Double>> embeddings = model.embedDocuments(texts).join();

        assertThat(embeddings).hasSize(3);
        assertThat(embeddings).allSatisfy(embedding -> assertThat(embedding).hasSize(384));
    }

    @Test
    void dimension() {
        ConcreteEmbedding model = new ConcreteEmbedding();

        assertThat(model.getDimension()).isEqualTo(384);
    }

    private static final class ConcreteEmbedding extends Embedding {

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(vector());
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(texts.stream().map(ignored -> vector()).toList());
        }

        @Override
        public int getDimension() {
            return 384;
        }

        private List<Double> vector() {
            return java.util.stream.IntStream.range(0, 384).mapToObj(ignored -> 0.1d).toList();
        }
    }
}

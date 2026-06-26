/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.foundation.store.base_reranker.Document;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestReranker} in
 * {@code tests/unit_tests/core/retrieval/reranker/test_base.py}.
 */
class RerankerBasePythonParityTest {

    @Test
    void rerankWithStrings() {
        ConcreteReranker model = new ConcreteReranker();
        List<Object> docs = List.of("doc1", "doc2", "doc3");

        Map<String, Double> result = model.rerank("test query", docs, Boolean.TRUE, Map.of()).join();

        assertScores(result, "doc1", "doc2", "doc3");
    }

    @Test
    void rerankWithDocuments() {
        ConcreteReranker model = new ConcreteReranker();
        List<Object> docs = List.of(
                Document.builder().text("First document").id("doc1").build(),
                Document.builder().text("Second document").id("doc2").build()
        );

        Map<String, Double> result = model.rerank("test query", docs, Boolean.TRUE, Map.of()).join();

        assertScores(result, "doc1", "doc2");
    }

    @Test
    void rerankWithMixedInput() {
        ConcreteReranker model = new ConcreteReranker();
        List<Object> docs = List.of("doc1", Document.builder().text("Second document").id("doc2").build());

        Map<String, Double> result = model.rerank("test query", docs, Boolean.TRUE, Map.of()).join();

        assertScores(result, "doc1", "doc2");
    }

    @Test
    void rerankWithInstructTrue() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerank("test query", List.of("doc1"), Boolean.TRUE, Map.of()).join();

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rerankWithInstructFalse() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerank("test query", List.of("doc1"), Boolean.FALSE, Map.of()).join();

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo(Boolean.FALSE);
    }

    @Test
    void rerankWithCustomInstruct() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerank("test query", List.of("doc1"), "Custom instruction", Map.of()).join();

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo("Custom instruction");
    }

    @Test
    void rerankSyncWithStrings() {
        ConcreteReranker model = new ConcreteReranker();
        List<Object> docs = List.of("doc1", "doc2", "doc3");

        Map<String, Double> result = model.rerankSync("test query", docs, Boolean.TRUE, Map.of());

        assertScores(result, "doc1", "doc2", "doc3");
    }

    @Test
    void rerankSyncWithDocuments() {
        ConcreteReranker model = new ConcreteReranker();
        List<Object> docs = List.of(
                Document.builder().text("First document").id("doc1").build(),
                Document.builder().text("Second document").id("doc2").build()
        );

        Map<String, Double> result = model.rerankSync("test query", docs, Boolean.TRUE, Map.of());

        assertScores(result, "doc1", "doc2");
    }

    @Test
    void rerankSyncWithInstructTrue() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerankSync("test query", List.of("doc1"), Boolean.TRUE, Map.of());

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rerankSyncWithInstructFalse() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerankSync("test query", List.of("doc1"), Boolean.FALSE, Map.of());

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo(Boolean.FALSE);
    }

    @Test
    void rerankSyncWithCustomInstruct() {
        ConcreteReranker model = new ConcreteReranker();

        Map<String, Double> result = model.rerankSync("test query", List.of("doc1"), "Custom instruction", Map.of());

        assertScores(result, "doc1");
        assertThat(model.lastInstruct).isEqualTo("Custom instruction");
    }

    private static void assertScores(Map<String, Double> result, String... documentIds) {
        assertThat(result).hasSize(documentIds.length);
        for (String documentId : documentIds) {
            assertThat(result).containsEntry(documentId, 0.5d);
        }
    }

    private static final class ConcreteReranker extends Reranker {
        private Object lastInstruct;

        @Override
        public CompletableFuture<Map<String, Double>> rerank(
                String query,
                List<Object> doc,
                Object instruct,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(rerankSync(query, doc, instruct, kwargs));
        }

        @Override
        public Map<String, Double> rerankSync(
                String query,
                List<Object> doc,
                Object instruct,
                Map<String, Object> kwargs
        ) {
            this.lastInstruct = instruct;
            Map<String, Double> result = new LinkedHashMap<>();
            for (Object item : doc) {
                if (item instanceof Document document) {
                    result.put(document.getId(), 0.5d);
                } else {
                    result.put(String.valueOf(item), 0.5d);
                }
            }
            return result;
        }
    }
}

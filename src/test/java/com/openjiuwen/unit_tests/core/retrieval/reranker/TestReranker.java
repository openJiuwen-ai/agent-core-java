/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.reranker.Reranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reranker abstract base class test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/reranker/test_base.py}.</p>
 */
class TestReranker {

    private static final class ConcreteReranker implements Reranker {

        @Override
        public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
            return new ArrayList<>(candidates.subList(0, Math.min(topK, candidates.size())));
        }

        @Override
        public Map<String, Double> rerankScores(
                String query, List<?> documents, Object instruct, Map<String, Object> options) {
            Map<String, Double> result = new LinkedHashMap<>();
            for (Object doc : documents) {
                if (doc instanceof String text) {
                    result.put(text, 0.5);
                } else if (doc instanceof Document document) {
                    result.put(document.getId(), 0.5);
                } else if (doc instanceof RetrievalResult retrievalResult) {
                    result.put(retrievalResult.getDocId() != null ? retrievalResult.getDocId() : retrievalResult.getText(), 0.5);
                }
            }
            return result;
        }
    }

    private final ConcreteReranker reranker = new ConcreteReranker();

    @Test
    @DisplayName("rerank with string documents")
    void testRerankWithStrings() {
        List<String> docs = List.of("doc1", "doc2", "doc3");
        Map<String, Double> result = reranker.rerankScores("test query", docs, true, Map.of());
        assertEquals(3, result.size());
        assertTrue(docs.stream().allMatch(result::containsKey));
        assertTrue(result.values().stream().allMatch(score -> score == 0.5));
    }

    @Test
    @DisplayName("rerank with Document objects")
    void testRerankWithDocuments() {
        List<Document> docs = List.of(
                new Document("doc1", "First document"),
                new Document("doc2", "Second document"));
        Map<String, Double> result = reranker.rerankScores("test query", docs, true, Map.of());
        assertEquals(2, result.size());
        assertEquals(0.5, result.get("doc1"));
        assertEquals(0.5, result.get("doc2"));
    }

    @Test
    @DisplayName("rerank with mixed string and Document inputs")
    void testRerankWithMixedInput() {
        List<Object> docs = List.of("doc1", new Document("doc2", "Second document"));
        Map<String, Double> result = reranker.rerankScores("test query", docs, true, Map.of());
        assertEquals(2, result.size());
        assertTrue(result.containsKey("doc1"));
        assertTrue(result.containsKey("doc2"));
    }

    @Test
    @DisplayName("rerank with instruct=true")
    void testRerankWithInstructTrue() {
        Map<String, Double> result = reranker.rerankScores("test query", List.of("doc1"), true, Map.of());
        assertTrue(result.containsKey("doc1"));
    }

    @Test
    @DisplayName("rerank with instruct=false")
    void testRerankWithInstructFalse() {
        Map<String, Double> result = reranker.rerankScores("test query", List.of("doc1"), false, Map.of());
        assertTrue(result.containsKey("doc1"));
    }

    @Test
    @DisplayName("rerank with custom instruct")
    void testRerankWithCustomInstruct() {
        Map<String, Double> result =
                reranker.rerankScores("test query", List.of("doc1"), "Custom instruction", Map.of());
        assertTrue(result.containsKey("doc1"));
    }

    @Test
    @DisplayName("rerank list returns ranked retrieval results")
    void testRerankList() {
        List<RetrievalResult> input = List.of(
                new RetrievalResult("doc1 text", 0.9, Map.of(), "doc1", null),
                new RetrievalResult("doc2 text", 0.8, Map.of(), "doc2", null));
        List<RetrievalResult> result = reranker.rerank("test query", input, 10);
        assertEquals(2, result.size());
        assertEquals("doc1 text", result.get(0).getText());
        assertEquals("doc2 text", result.get(1).getText());
    }

    @Test
    @DisplayName("rerank honors topK")
    void testRerankHonorsTopK() {
        List<RetrievalResult> input = List.of(
                new RetrievalResult("doc1 text", 0.9, Map.of(), "doc1", null),
                new RetrievalResult("doc2 text", 0.8, Map.of(), "doc2", null),
                new RetrievalResult("doc3 text", 0.7, Map.of(), "doc3", null));
        List<RetrievalResult> result = reranker.rerank("test query", input, 2);
        assertEquals(2, result.size());
        assertEquals("doc1", result.get(0).getDocId());
        assertEquals("doc2", result.get(1).getDocId());
    }

    @Test
    @DisplayName("reranker interface exists")
    void testRerankerInterfaceExists() {
        assertNotNull(Reranker.class);
        assertInstanceOf(Reranker.class, reranker);
    }
}

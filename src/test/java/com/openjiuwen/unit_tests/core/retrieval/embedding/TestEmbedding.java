/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.embedding.Embedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding model abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_base.py
 */
class TestEmbedding {

    /**
     * Concrete embedding model implementation for testing abstract base class.
     */
    static class ConcreteEmbedding implements Embedding {

        @Override
        public List<Float> embedQuery(String text) {
            List<Float> embedding = new ArrayList<>();
            for (int i = 0; i < 384; i++) {
                embedding.add(0.1f);
            }
            return embedding;
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            List<List<Float>> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                List<Float> embedding = new ArrayList<>();
                for (int j = 0; j < 384; j++) {
                    embedding.add(0.1f);
                }
                embeddings.add(embedding);
            }
            return embeddings;
        }

        @Override
        public int getDimension() {
            return 384;
        }
    }

    @Test
    void testEmbedQuery() {
        // Test embedding query text
        Embedding model = new ConcreteEmbedding();
        List<Float> embedding = model.embedQuery("test query");
        assertEquals(384, embedding.size());
        assertTrue(embedding.stream().allMatch(x -> x instanceof Float));
    }

    @Test
    void testEmbedDocuments() {
        // Test embedding document texts
        Embedding model = new ConcreteEmbedding();
        List<String> texts = List.of("text 1", "text 2", "text 3");
        List<List<Float>> embeddings = model.embedDocuments(texts, null);
        assertEquals(3, embeddings.size());
        assertTrue(embeddings.stream().allMatch(emb -> emb.size() == 384));
    }

    @Test
    void testDimension() {
        // Test dimension property
        Embedding model = new ConcreteEmbedding();
        assertEquals(384, model.getDimension());
    }

    @Test
    void testDefaultMaxBatchSize() {
        // Test default max batch size
        Embedding model = new ConcreteEmbedding();
        assertEquals(256, model.getMaxBatchSize());
    }
}
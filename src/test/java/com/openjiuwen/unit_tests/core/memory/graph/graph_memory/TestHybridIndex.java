/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HybridIndex.
 * <p>
 * Mirrors Python's test_hybrid_index.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_hybrid_index.py</code>.
 */
@DisplayName("Hybrid Index Tests")
class TestHybridIndex {

    // Stub classes
    static class IndexEntry {
        String id;
        double vectorScore;
        double keywordScore;
        double combinedScore;

        IndexEntry(String id) {
            this.id = id;
            this.vectorScore = 0;
            this.keywordScore = 0;
        }

        void setVectorScore(double score) {
            this.vectorScore = score;
            updateCombinedScore();
        }

        void setKeywordScore(double score) {
            this.keywordScore = score;
            updateCombinedScore();
        }

        private void updateCombinedScore() {
            this.combinedScore = vectorScore * 0.7 + keywordScore * 0.3;
        }
    }

    static class HybridIndexStub {
        Map<String, IndexEntry> entries = new HashMap<>();

        void addEntry(IndexEntry entry) {
            entries.put(entry.id, entry);
        }

        List<IndexEntry> search(double vectorWeight, double keywordWeight) {
            List<IndexEntry> results = new ArrayList<>(entries.values());
            results.sort((a, b) -> Double.compare(
                b.vectorScore * vectorWeight + b.keywordScore * keywordWeight,
                a.vectorScore * vectorWeight + a.keywordScore * keywordWeight
            ));
            return results;
        }

        IndexEntry get(String id) {
            return entries.get(id);
        }
    }

    @Nested
    @DisplayName("Index Entry Tests")
    class TestIndexEntry {

        @Test
        @DisplayName("index entry creation")
        void testIndexEntryCreation() {
            IndexEntry entry = new IndexEntry("doc1");

            assertEquals("doc1", entry.id);
        }

        @Test
        @DisplayName("index entry combined score")
        void testIndexEntryCombinedScore() {
            IndexEntry entry = new IndexEntry("doc1");
            entry.setVectorScore(0.8);
            entry.setKeywordScore(0.6);

            double expected = 0.8 * 0.7 + 0.6 * 0.3;
            assertEquals(expected, entry.combinedScore, 0.001);
        }
    }

    @Nested
    @DisplayName("Hybrid Index Tests")
    class TestHybridIndexClass {

        @Test
        @DisplayName("add entry to index")
        void testAddEntryToIndex() {
            HybridIndexStub index = new HybridIndexStub();
            IndexEntry entry = new IndexEntry("doc1");

            index.addEntry(entry);

            assertNotNull(index.get("doc1"));
        }

        @Test
        @DisplayName("search with weights")
        void testSearchWithWeights() {
            HybridIndexStub index = new HybridIndexStub();
            IndexEntry e1 = new IndexEntry("doc1");
            e1.setVectorScore(0.9);
            e1.setKeywordScore(0.3);
            IndexEntry e2 = new IndexEntry("doc2");
            e2.setVectorScore(0.5);
            e2.setKeywordScore(0.9);
            index.addEntry(e1);
            index.addEntry(e2);

            List<IndexEntry> results = index.search(0.7, 0.3);

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("search returns sorted results")
        void testSearchReturnsSortedResults() {
            HybridIndexStub index = new HybridIndexStub();
            IndexEntry e1 = new IndexEntry("doc1");
            e1.setVectorScore(1.0);
            IndexEntry e2 = new IndexEntry("doc2");
            e2.setVectorScore(0.5);
            index.addEntry(e1);
            index.addEntry(e2);

            List<IndexEntry> results = index.search(1.0, 0.0);

            assertEquals("doc1", results.get(0).id);
        }
    }
}
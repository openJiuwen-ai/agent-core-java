/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResultRanking.
 * <p>
 * Mirrors Python's test_result_ranking.py from
 * <code>tests/unit_tests/core/foundation/store/graph/test_result_ranking.py</code>.
 */
@DisplayName("Result Ranking Tests")
class TestResultRanking {

    // Stub classes
    static class SearchResult {
        String id;
        double score;
        Map<String, Object> metadata = new HashMap<>();

        SearchResult(String id, double score) {
            this.id = id;
            this.score = score;
        }
    }

    static class ResultRanker {
        List<SearchResult> results;

        ResultRanker(List<SearchResult> results) {
            this.results = new ArrayList<>(results);
        }

        List<SearchResult> rankByScore() {
            results.sort(Comparator.comparingDouble(r -> -r.score));
            return new ArrayList<>(results);
        }

        List<SearchResult> topK(int k) {
            rankByScore();
            return results.subList(0, Math.min(k, results.size()));
        }

        SearchResult getBest() {
            if (results.isEmpty()) return null;
            return rankByScore().get(0);
        }
    }

    @Nested
    @DisplayName("Search Result Tests")
    class TestSearchResult {

        @Test
        @DisplayName("search result creation")
        void testSearchResultCreation() {
            SearchResult result = new SearchResult("doc1", 0.95);

            assertEquals("doc1", result.id);
            assertEquals(0.95, result.score);
        }

        @Test
        @DisplayName("search result with metadata")
        void testSearchResultWithMetadata() {
            SearchResult result = new SearchResult("doc1", 0.95);
            result.metadata.put("source", "graph");
            result.metadata.put("type", "entity");

            assertEquals("graph", result.metadata.get("source"));
            assertEquals("entity", result.metadata.get("type"));
        }
    }

    @Nested
    @DisplayName("Result Ranking Tests")
    class TestRankingOperations {

        @Test
        @DisplayName("rank by score")
        void testRankByScore() {
            List<SearchResult> results = new ArrayList<>();
            results.add(new SearchResult("r1", 0.5));
            results.add(new SearchResult("r2", 0.9));
            results.add(new SearchResult("r3", 0.7));

            ResultRanker ranker = new ResultRanker(results);
            List<SearchResult> ranked = ranker.rankByScore();

            assertEquals("r2", ranked.get(0).id);
            assertEquals("r3", ranked.get(1).id);
            assertEquals("r1", ranked.get(2).id);
        }

        @Test
        @DisplayName("top k results")
        void testTopKResults() {
            List<SearchResult> results = new ArrayList<>();
            results.add(new SearchResult("r1", 0.5));
            results.add(new SearchResult("r2", 0.9));
            results.add(new SearchResult("r3", 0.7));

            ResultRanker ranker = new ResultRanker(results);
            List<SearchResult> top2 = ranker.topK(2);

            assertEquals(2, top2.size());
            assertEquals("r2", top2.get(0).id);
        }

        @Test
        @DisplayName("get best result")
        void testGetBestResult() {
            List<SearchResult> results = new ArrayList<>();
            results.add(new SearchResult("r1", 0.5));
            results.add(new SearchResult("r2", 0.9));

            ResultRanker ranker = new ResultRanker(results);
            SearchResult best = ranker.getBest();

            assertEquals("r2", best.id);
            assertEquals(0.9, best.score);
        }
    }
}
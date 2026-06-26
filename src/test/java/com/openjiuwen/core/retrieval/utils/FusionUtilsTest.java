/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestRRFFusion} in
 * {@code tests/unit_tests/core/retrieval/utils/test_fusion.py}.
 */
class FusionUtilsTest {

    @Test
    void rrfFusionSingleListSortsByRrfScore() {
        RetrievalResult first = retrieval("Result 1", 0.9);
        RetrievalResult second = retrieval("Result 2", 0.8);
        RetrievalResult third = retrieval("Result 3", 0.7);

        List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of(List.of(first, second, third)));

        assertThat(fused).containsExactly(first, second, third);
        assertThat(fused.getFirst().getText()).isEqualTo("Result 1");
        assertThat(fused.getFirst().getScore()).isGreaterThan(fused.get(1).getScore());
    }

    @Test
    void rrfFusionRetrievalPreservesFirstOccurrenceAndRanksByFusedScore() {
        RetrievalResult alphaFirst = new RetrievalResult("alpha", 0.9, Map.of("source", "first"), "d1", "c1");
        RetrievalResult beta = new RetrievalResult("beta", 0.8, Map.of("source", "second"), "d2", "c2");
        RetrievalResult alphaSecond = new RetrievalResult("alpha", 0.1, Map.of("source", "later"), "d3", "c3");

        List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(
                List.of(
                        List.of(alphaFirst, beta),
                        List.of(alphaSecond)
                ),
                60
        );

        assertThat(fused).containsExactly(alphaFirst, beta);
        assertThat(alphaFirst.getMetadata()).containsEntry("source", "first");
        assertThat(alphaFirst.getScore()).isGreaterThan(beta.getScore());
    }

    @Test
    void rrfFusionEmptyListReturnsEmptyList() {
        List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of());

        assertThat(fused).isEmpty();
    }

    @Test
    void rrfFusionWithEmptyResultsKeepsNonEmptyResults() {
        RetrievalResult only = retrieval("Result 1", 0.9);

        List<RetrievalResult> fused = FusionUtils.rrfFusionRetrieval(List.of(List.of(only), List.of()));

        assertThat(fused).containsExactly(only);
        assertThat(fused.getFirst().getText()).isEqualTo("Result 1");
    }

    @Test
    void rrfFusionCustomKPreservesResultCountAndChangesScoreScale() {
        List<RetrievalResult> k30Results1 = List.of(retrieval("Result 1", 0.9), retrieval("Result 2", 0.8));
        List<RetrievalResult> k30Results2 = List.of(retrieval("Result 2", 0.85), retrieval("Result 3", 0.7));
        List<RetrievalResult> fusedK30 = FusionUtils.rrfFusionRetrieval(List.of(k30Results1, k30Results2), 30);
        double k30FirstScore = fusedK30.getFirst().getScore();

        List<RetrievalResult> k60Results1 = List.of(retrieval("Result 1", 0.9), retrieval("Result 2", 0.8));
        List<RetrievalResult> k60Results2 = List.of(retrieval("Result 2", 0.85), retrieval("Result 3", 0.7));
        List<RetrievalResult> fusedK60 = FusionUtils.rrfFusionRetrieval(List.of(k60Results1, k60Results2), 60);

        assertThat(fusedK30).hasSize(3);
        assertThat(fusedK60).hasSize(3);
        assertThat(k30FirstScore).isNotEqualTo(fusedK60.getFirst().getScore());
    }

    @Test
    void rrfFusionSearchSupportsDefaultKAndUpdatesScoresInPlace() {
        SearchResult left = new SearchResult("1", "shared", 0.0, Map.of());
        SearchResult right = new SearchResult("2", "unique", 0.0, Map.of());

        List<SearchResult> fused = FusionUtils.rrfFusionSearch(
                List.of(
                        List.of(left, right),
                        List.of(new SearchResult("3", "shared", 0.0, Map.of()))
                )
        );

        assertThat(fused).containsExactly(left, right);
        assertThat(left.getScore()).isCloseTo((1.0 / 61.0) + (1.0 / 61.0), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(right.getScore()).isCloseTo(1.0 / 62.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    private static RetrievalResult retrieval(String text, double score) {
        return new RetrievalResult(text, score, Map.of(), null, null);
    }
}

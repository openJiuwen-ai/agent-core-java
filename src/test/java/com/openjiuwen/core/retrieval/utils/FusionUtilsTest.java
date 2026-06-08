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

class FusionUtilsTest {

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
}

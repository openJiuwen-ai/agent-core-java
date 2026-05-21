/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.utils;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.utils.FusionUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fusion utility function test cases.
 *
 * <p>Mirrors Python's {@code test_fusion.py} in
 * {@code tests/unit_tests/core/retrieval/utils/test_fusion}.</p>
 */
@DisplayName("RRF Fusion Tests")
class TestFusion {

    @Nested
    @DisplayName("RRF Fusion")
    class RrfFusionTests {

        @Test
        @DisplayName("test_rrf_fusion_single_list - single result list fusion")
        void testRrfFusionSingleList() {
            List<RetrievalResult> results = new ArrayList<>();
            results.add(new RetrievalResult("id1", "Result 1", 0.9, null));
            results.add(new RetrievalResult("id2", "Result 2", 0.8, null));
            results.add(new RetrievalResult("id3", "Result 3", 0.7, null));

            List<List<RetrievalResult>> input = new ArrayList<>();
            input.add(results);

            List<RetrievalResult> fused = FusionUtils.rrfFusion(input, 60);

            assertThat(fused).hasSize(3);
            assertThat(fused.get(0).getText()).isEqualTo("Result 1");
        }

        @Test
        @DisplayName("test_rrf_fusion_multiple_lists - multiple result lists fusion")
        void testRrfFusionMultipleLists() {
            List<RetrievalResult> results1 = new ArrayList<>();
            results1.add(new RetrievalResult("id1", "Result 1", 0.9, null));
            results1.add(new RetrievalResult("id2", "Result 2", 0.8, null));

            List<RetrievalResult> results2 = new ArrayList<>();
            results2.add(new RetrievalResult("id2", "Result 2", 0.85, null));
            results2.add(new RetrievalResult("id3", "Result 3", 0.7, null));

            List<List<RetrievalResult>> input = new ArrayList<>();
            input.add(results1);
            input.add(results2);

            List<RetrievalResult> fused = FusionUtils.rrfFusion(input, 60);

            assertThat(fused).hasSize(3);
        }

        @Test
        @DisplayName("test_rrf_fusion_empty_list - empty list fusion")
        void testRrfFusionEmptyList() {
            List<List<RetrievalResult>> input = new ArrayList<>();
            List<RetrievalResult> fused = FusionUtils.rrfFusion(input, 60);

            assertThat(fused).isEmpty();
        }

        @Test
        @DisplayName("test_rrf_fusion_with_empty_results - fusion with empty results")
        void testRrfFusionWithEmptyResults() {
            List<RetrievalResult> results1 = new ArrayList<>();
            results1.add(new RetrievalResult("id1", "Result 1", 0.9, null));

            List<RetrievalResult> results2 = new ArrayList<>();

            List<List<RetrievalResult>> input = new ArrayList<>();
            input.add(results1);
            input.add(results2);

            List<RetrievalResult> fused = FusionUtils.rrfFusion(input, 60);

            assertThat(fused).hasSize(1);
            assertThat(fused.get(0).getText()).isEqualTo("Result 1");
        }

        @Test
        @DisplayName("test_rrf_fusion_custom_k - custom k parameter")
        void testRrfFusionCustomK() {
            List<RetrievalResult> results1 = new ArrayList<>();
            results1.add(new RetrievalResult("id1", "Result 1", 0.9, null));
            results1.add(new RetrievalResult("id2", "Result 2", 0.8, null));

            List<RetrievalResult> results2 = new ArrayList<>();
            results2.add(new RetrievalResult("id2", "Result 2", 0.85, null));
            results2.add(new RetrievalResult("id3", "Result 3", 0.7, null));

            List<List<RetrievalResult>> input = new ArrayList<>();
            input.add(results1);
            input.add(results2);

            List<RetrievalResult> fusedK30 = FusionUtils.rrfFusion(input, 30);
            List<RetrievalResult> fusedK60 = FusionUtils.rrfFusion(input, 60);

            assertThat(fusedK30).hasSize(3);
            assertThat(fusedK60).hasSize(3);
        }
    }
}
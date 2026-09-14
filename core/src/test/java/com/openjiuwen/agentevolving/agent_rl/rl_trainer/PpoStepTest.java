/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.rl_trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpoStepTest {

    @Test
    void runPpoStepInvokesExecutorPipelineAndProcessesMetrics() {
        RecordingExecutor executor = new RecordingExecutor(2);
        PpoStep.Batch batch = PpoStep.Batch.of(Map.of("sample_id", List.of("a", "b")));
        Object originBatch = new Object();

        Map<String, Object> metrics = PpoStep.runPpoStep(executor, originBatch, batch);

        assertEquals(List.of(
                "baseline",
                "reward",
                "old_log_prob",
                "ref",
                "values",
                "adv",
                "filter_groups",
                "balance_batch",
                "update_critic",
                "update_actor",
                "process_metrics"
        ), executor.events);
        assertSame(originBatch, executor.originBatchSeen);
        assertEquals(0, metrics.get("training/n_triplets_dropped_remainder"));
        assertEquals(true, metrics.get("processed"));
        @SuppressWarnings("unchecked")
        Map<String, Double> timing = (Map<String, Double>) metrics.get("timing_raw");
        assertTrue(timing.keySet().containsAll(List.of(
                "step",
                "gen_max",
                "reward",
                "old_log_prob",
                "ref",
                "values",
                "adv",
                "filter_groups",
                "data_alignment",
                "balance_batch",
                "update_critic",
                "update_actor"
        )));
    }

    @Test
    void dataAlignmentDropsMaskedRowsAndFloorsToMiniBatchSize() {
        RecordingExecutor executor = new RecordingExecutor(2);
        Map<String, Object> metrics = new LinkedHashMap<>();
        PpoStep.Batch batch = PpoStep.Batch.of(Map.of(
                "sample_id", List.of("a", "b", "c", "d", "e"),
                "is_drop_mask", List.of(false, true, false, false, false)
        ));

        PpoStep.Batch aligned = PpoStep.runDataAlignment(executor, batch, metrics);

        assertEquals(4, aligned.length());
        assertEquals(1, metrics.get("training/n_triplets_prompt_too_long"));
        assertEquals(0, metrics.get("training/n_triplets_dropped_remainder"));
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) aligned.get("sample_id");
        assertFalse(ids.contains("b"));
    }

    @Test
    void emptyAlignedBatchSkipsModelUpdatesAndProcessMetrics() {
        RecordingExecutor executor = new RecordingExecutor(4);
        PpoStep.Batch batch = PpoStep.Batch.of(Map.of("sample_id", List.of("a", "b")));

        Map<String, Object> metrics = PpoStep.runPpoStep(executor, null, batch);

        assertEquals(1, metrics.get("training/skipped_empty_batch"));
        assertEquals(2, metrics.get("training/n_triplets_dropped_remainder"));
        assertFalse(executor.events.contains("balance_batch"));
        assertFalse(executor.events.contains("update_actor"));
        assertFalse(executor.events.contains("process_metrics"));
    }

    @Test
    void clampsNonFiniteTrainingTensorsBeforeUpdates() {
        RecordingExecutor executor = new RecordingExecutor(2);
        PpoStep.Batch batch = PpoStep.Batch.of(Map.of(
                "sample_id", List.of("a", "b"),
                "advantages", new double[] {Double.NaN, 1.5},
                "old_log_probs", List.of(Double.POSITIVE_INFINITY, -0.2),
                "token_level_rewards", new double[][] {
                        {Double.NEGATIVE_INFINITY, 2.0},
                        {Double.NaN, 3.0}
                }
        ));

        PpoStep.runPpoStep(executor, null, batch);

        assertArrayEquals(new double[] {0.0, 1.5}, (double[]) executor.batchSeenByBalance.get("advantages"), 1e-9);
        assertEquals(List.of(0.0, -0.2), executor.batchSeenByBalance.get("old_log_probs"));
        double[][] rewards = (double[][]) executor.batchSeenByBalance.get("token_level_rewards");
        assertArrayEquals(new double[] {0.0, 2.0}, rewards[0], 1e-9);
        assertArrayEquals(new double[] {0.0, 3.0}, rewards[1], 1e-9);
    }

    private static final class RecordingExecutor implements PpoStep.TrainingExecutor {
        private final int miniBatchSize;
        private final List<String> events = new ArrayList<>();
        private Object originBatchSeen;
        private Map<String, Object> batchSeenByBalance;

        private RecordingExecutor(int miniBatchSize) {
            this.miniBatchSize = miniBatchSize;
        }

        @Override
        public int getMiniBatchSize() {
            return miniBatchSize;
        }

        @Override
        public PpoStep.Batch computeBaseline(Object originBatch, PpoStep.Batch batch) {
            events.add("baseline");
            originBatchSeen = originBatch;
            return batch;
        }

        @Override
        public PpoStep.Batch computeReward(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("reward");
            metrics.put("reward_done", true);
            return batch;
        }

        @Override
        public PpoStep.Batch computeOldLogProb(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("old_log_prob");
            return batch;
        }

        @Override
        public PpoStep.Batch computeReferenceLogProb(PpoStep.Batch batch) {
            events.add("ref");
            return batch;
        }

        @Override
        public PpoStep.Batch computeValues(PpoStep.Batch batch) {
            events.add("values");
            return batch;
        }

        @Override
        public PpoStep.Batch computeAdvantages(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("adv");
            return batch;
        }

        @Override
        public PpoStep.Batch filterEffectiveGroups(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("filter_groups");
            return batch;
        }

        @Override
        public void balanceBatch(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("balance_batch");
            batchSeenByBalance = new LinkedHashMap<>(batch.getBatch());
        }

        @Override
        public void updateCritic(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("update_critic");
        }

        @Override
        public void updateActor(PpoStep.Batch batch, Map<String, Object> metrics) {
            events.add("update_actor");
        }

        @Override
        public Map<String, Object> processMetrics(PpoStep.Batch batch,
                                                  Map<String, Object> metrics,
                                                  Map<String, Double> timingRaw) {
            events.add("process_metrics");
            Map<String, Object> processed = new LinkedHashMap<>(metrics);
            processed.put("processed", true);
            processed.put("timing_raw", new LinkedHashMap<>(timingRaw));
            return processed;
        }
    }
}

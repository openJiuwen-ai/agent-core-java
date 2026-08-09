/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.rl_trainer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerlExecutorTest {

    @AfterEach
    void tearDown() {
        VerlExecutor.shutdown();
    }

    @Test
    void staticExecuteStepUsesInitializedOfflineExecutor() {
        VerlExecutor.initialize(config(1));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) VerlExecutor.executeStep(Map.of(
                "responses", new int[][] {{1}, {2}},
                "token_level_scores", new double[][] {{0.5}, {1.0}}
        ));

        assertEquals(2, metrics.get("training/batch_size"));
        assertEquals(1, metrics.get("actor/update_called"));
        assertTrue(metrics.containsKey("timing_raw"));
        assertTrue(VerlExecutor.isInitialized());
    }

    @Test
    void defaultExecutorPreparesRewardLogProbAdvantagesAndMetrics() {
        VerlExecutor executor = new VerlExecutor(config(2));
        executor.setUseReferencePolicy(true);
        executor.setUseCritic(true);
        executor.setBalanceBatch(true);
        executor.setGpuCount(3);

        PpoStep.Batch batch = new PpoStep.Batch(
                Map.of(
                        "responses", new int[][] {{7, 8}, {9, 0}},
                        "attention_mask", new int[][] {{1, 1, 1}, {1, 1, 0}},
                        "token_level_scores", new double[][] {{0.25, 0.75}, {1.5, 0.0}}
                ),
                Map.of("data_id_list", List.of("case-a", "case-b"))
        );

        Map<String, Object> metrics = executor.trainStep(batch, batch);

        assertEquals(List.of("case-a", "case-b"), batch.getNonTensors().get("uid"));
        assertEquals(List.of(3, 2), batch.getNonTensors().get("global_token_num"));
        assertArrayEquals(new double[] {1.0, 1.5}, (double[]) batch.get("advantages"), 1e-9);
        assertTrue(batch.getBatch().containsKey("old_log_probs"));
        assertTrue(batch.getBatch().containsKey("ref_log_prob"));
        assertTrue(batch.getBatch().containsKey("values"));
        assertEquals(1, metrics.get("critic/update_called"));
        assertEquals(1, metrics.get("actor/update_called"));
        assertEquals(1, metrics.get("training/balance_batch"));
        assertEquals(3, metrics.get("training/n_gpus"));
    }

    @Test
    void filterEffectiveGroupsKeepsOnlyVariantPositiveToolGroups() {
        Map<String, Object> cfg = Map.of(
                "actor_rollout_ref", Map.of("actor", Map.of("ppo_mini_batch_size", 2)),
                "algorithm", Map.of("filter_groups", true)
        );
        VerlExecutor executor = new VerlExecutor(cfg);
        PpoStep.Batch batch = new PpoStep.Batch(
                Map.of(
                        "sample_id", List.of("a", "b", "c", "d", "e"),
                        "responses", new int[][] {{1}, {1}, {1}, {1}, {1}},
                        "token_level_scores", new double[][] {{1.0}, {0.0}, {0.5}, {0.5}, {2.0}}
                ),
                Map.of(
                        "uid", List.of("g1", "g1", "g2", "g2", "g3"),
                        "n_turns_list", List.of(2, 1, 2, 2, 2)
                )
        );

        Map<String, Object> metrics = executor.trainStep(batch, batch);

        assertEquals(3, metrics.get("training/filter_groups_total"));
        assertEquals(1, metrics.get("training/filter_groups_kept"));
        assertEquals(1, metrics.get("training/filter_groups_no_variance"));
        assertEquals(1, metrics.get("training/filter_groups_singleton"));
        assertEquals(2, metrics.get("training/batch_size"));
    }

    @Test
    void offlineRolloutManagerSleepsAndWakes() {
        VerlExecutor.OfflineVerlTrainingExecutor executor =
                new VerlExecutor.OfflineVerlTrainingExecutor(config(1));
        FakeRolloutManager manager = new FakeRolloutManager(List.of("http://127.0.0.1:8000"));
        executor.setAsyncRolloutManager(manager);

        executor.sleepRollout();
        List<String> addresses = executor.wakeUpRollout();

        assertTrue(manager.slept);
        assertTrue(manager.awake);
        assertEquals(List.of("http://127.0.0.1:8000"), addresses);
    }

    @Test
    void onlineRolloutManagerUpdatesWeightsAndReturnsAddresses() {
        VerlExecutor.OnlineVerlTrainingExecutor executor =
                new VerlExecutor.OnlineVerlTrainingExecutor(config(1));
        executor.setGlobalSteps(7);
        FakeCheckpointManager checkpointManager = new FakeCheckpointManager();
        FakeRolloutManager rolloutManager = new FakeRolloutManager(List.of("http://127.0.0.1:9000"));
        executor.setCheckpointManager(checkpointManager);
        executor.setAsyncRolloutManager(rolloutManager);

        executor.sleepRollout();
        List<String> addresses = executor.wakeUpRollout();

        assertTrue(checkpointManager.slept);
        assertEquals(7, checkpointManager.updatedStep);
        assertFalse(rolloutManager.awake);
        assertEquals(List.of("http://127.0.0.1:9000"), addresses);
    }

    private Map<String, Object> config(int miniBatchSize) {
        return Map.of(
                "actor_rollout_ref", Map.of("actor", Map.of("ppo_mini_batch_size", miniBatchSize)),
                "trainer", Map.of("critic_warmup", 0),
                "resource_pool_manager", Map.of("n_gpus", 1)
        );
    }

    private static final class FakeRolloutManager implements VerlExecutor.RolloutManager {
        private final List<String> addresses;
        private boolean slept;
        private boolean awake;

        private FakeRolloutManager(List<String> addresses) {
            this.addresses = new ArrayList<>(addresses);
        }

        @Override
        public void sleep() {
            slept = true;
        }

        @Override
        public void wakeUp() {
            awake = true;
        }

        @Override
        public List<String> getServerAddresses() {
            return addresses;
        }
    }

    private static final class FakeCheckpointManager implements VerlExecutor.CheckpointManager {
        private boolean slept;
        private int updatedStep = -1;

        @Override
        public void sleepReplicas() {
            slept = true;
        }

        @Override
        public void updateWeights(int globalSteps) {
            updatedStep = globalSteps;
        }
    }
}

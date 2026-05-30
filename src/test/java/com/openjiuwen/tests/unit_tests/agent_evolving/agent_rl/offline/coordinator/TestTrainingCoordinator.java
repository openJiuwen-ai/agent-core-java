/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TrainingCoordinator;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TrainingCoordinator.
 * <p>
 * Mirrors Python's {@code test_training_coordinator.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/}.
 */
@DisplayName("TrainingCoordinator Tests")
class TestTrainingCoordinator {

    @Nested
    @DisplayName("Init And Config")
    class TestInitAndConfig {

        @Test
        @DisplayName("init with legal config succeeds")
        void testInitWithLegalConfigSucceeds() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));

            assertThat(coordinator.isWholeTrajectory()).isFalse();
            assertThat(coordinator.getBatchBuilder().getPadTokenId()).isEqualTo(99);
            assertThat(coordinator.getBatchBuilder().getMaxPromptLength()).isEqualTo(32);
            assertThat(coordinator.getBatchBuilder().getMaxResponseLength()).isEqualTo(16);
        }

        @Test
        @DisplayName("init with missing whole_trajectory defaults false")
        void testInitWithMissingWholeTrajectoryDefaultsFalse() {
            Map<String, Object> config = config(false, "default_sampling");
            ((Map<?, ?>) config.get("JiuwenRL")).remove("whole_trajectory");

            TrainingCoordinator coordinator = newCoordinator(config);

            assertThat(coordinator.isWholeTrajectory()).isFalse();
        }
    }

    @Nested
    @DisplayName("Build Initial Tasks")
    class TestBuildInitialTasks {

        @Test
        @DisplayName("build initial tasks returns dict keyed by task id")
        void testBuildInitialTasksReturnsDictKeyedByTaskId() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));
            Map<String, List<?>> rlData = new HashMap<>();
            rlData.put("col_a", List.of(1, 2));
            rlData.put("col_b", List.of(3, 4));

            Map<String, RLTask> tasks = coordinator.buildInitialTasks(rlData);

            assertThat(tasks).hasSize(4);
            assertThat(tasks.keySet()).doesNotContainNull();
            assertThat(tasks.values()).allSatisfy(task -> assertThat(task.getRoundNum()).isZero());
            assertThat(tasks.values())
                    .extracting(RLTask::getTaskSample)
                    .containsOnly(Map.of("col_a", 1, "col_b", 3), Map.of("col_a", 2, "col_b", 4));
            assertThat(tasks.values().stream().map(RLTask::getOriginTaskId).distinct().toList()).hasSize(2);
        }

        @Test
        @DisplayName("build initial tasks empty batch returns empty dict")
        void testBuildInitialTasksEmptyBatchReturnsEmptyDict() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));
            Map<String, List<?>> rlData = Map.of("col_a", List.of(), "col_b", List.of());

            assertThat(coordinator.buildInitialTasks(rlData)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Caches And Iteration")
    class TestCachesAndIteration {

        @Test
        @DisplayName("merge caches unifies by uid")
        void testMergeCachesUnifiesByUid() {
            Map<String, List<RolloutWithReward>> pos = Map.of("u1", List.of(rolloutWithReward(0.5)));
            Map<String, List<RolloutWithReward>> neg = Map.of(
                    "u1", List.of(rolloutWithReward(-0.1)),
                    "u2", List.of()
            );

            Map<String, List<RolloutWithReward>> merged = TrainingCoordinator.mergeCaches(pos, neg);

            assertThat(merged.keySet()).containsExactlyInAnyOrder("u1", "u2");
            assertThat(merged.get("u1")).hasSize(2);
            assertThat(merged.get("u2")).isEmpty();
        }

        @Test
        @DisplayName("clear up data resets caches and datastore")
        void testClearUpDataResetsCachesAndDatastore() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));
            coordinator.getPositiveCache().put("u1", new ArrayList<>(List.of(rolloutWithReward(1.0))));
            coordinator.getNegativeCache().put("u2", new ArrayList<>(List.of(rolloutWithReward(-1.0))));
            coordinator.addRoundStats(1, List.of(0.5));
            coordinator.submitTask(new RLTask("task", "origin"));

            coordinator.clearUpData();

            assertThat(coordinator.getPositiveCache()).isEmpty();
            assertThat(coordinator.getNegativeCache()).isEmpty();
            assertThat(coordinator.getTurnCounts()).isEmpty();
            assertThat(coordinator.getRewardLists()).isEmpty();
            assertThat(coordinator.getDatastore().isFinished()).isTrue();
        }

        @Test
        @DisplayName("run iteration classifies collected rollouts through registry")
        void testRunIterationClassifiesCollectedRolloutsThroughRegistry() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));
            RLTask task = new RLTask("task-1", "origin-1", Map.of("prompt", "p"), 0);
            coordinator.submitTask(task);
            coordinator.getDatastore().getTask();
            coordinator.getDatastore().addRollout(rolloutMessage("task-1", "origin-1", 1.0, 2));

            coordinator.runIteration();

            assertThat(coordinator.getPositiveCache()).containsKey("origin-1");
            assertThat(coordinator.getPositiveCache().get("origin-1")).hasSize(2);
            assertThat(coordinator.getNegativeCache()).doesNotContainKey("origin-1");
            assertThat(coordinator.getTotalPositive()).isEqualTo(2);
            assertThat(coordinator.shouldStop()).isTrue();
        }
    }

    @Nested
    @DisplayName("Build RL Batch")
    class TestBuildRlBatch {

        @Test
        @DisplayName("build rl batch from caches returns batch and merged dict")
        void testBuildRlBatchFromCachesReturnsBatchAndMergedDict() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));
            coordinator.getPositiveCache().put("u1", new ArrayList<>(List.of(rolloutWithReward(0.5))));
            coordinator.getNegativeCache().put("u1", new ArrayList<>(List.of(rolloutWithReward(-0.1))));

            TrainingCoordinator.BatchBuildResult result = coordinator.buildRlBatchFromCaches(null);

            assertThat(result.rlBatch().batchSize).isEqualTo(2);
            assertThat(result.rlBatch().prompts).allSatisfy(prompt -> assertThat(prompt).hasSize(32));
            assertThat(result.rlBatch().responses).allSatisfy(response -> assertThat(response).hasSize(16));
            assertThat(result.mergedRollouts()).containsKey("u1");
            assertThat(result.mergedRollouts().get("u1")).hasSize(2);
        }

        @Test
        @DisplayName("sampling ada honors final keep per prompt")
        void testSamplingAdaHonorsFinalKeepPerPrompt() {
            Map<String, Object> cfg = config(false, "sampling_ada");
            ((Map<String, Object>) cfg.get("JiuwenRL")).put("final_keep_per_prompt", 4);
            TrainingCoordinator coordinator = newCoordinator(cfg);
            coordinator.getPositiveCache().put("u1", mutableRollouts(5, 1.0));
            coordinator.getNegativeCache().put("u1", mutableRollouts(5, -0.1));

            TrainingCoordinator.BatchBuildResult result = coordinator.buildRlBatchFromCaches(null);

            assertThat(result.rlBatch().batchSize).isEqualTo(4);
            assertThat(result.mergedRollouts().get("u1")).hasSize(4);
        }

        @Test
        @DisplayName("build rl batch from caches empty raises")
        void testBuildRlBatchFromCachesEmptyRaises() {
            TrainingCoordinator coordinator = newCoordinator(config(false, "default_sampling"));

            assertThatThrownBy(() -> coordinator.buildRlBatchFromCaches(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0 samples collected");
        }
    }

    private static TrainingCoordinator newCoordinator(Map<String, Object> config) {
        return new TrainingCoordinator(config, new TestTokenizer(), null);
    }

    private static Map<String, Object> config(boolean wholeTrajectory, String sampler) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("data", Map.of("max_prompt_length", 32, "max_response_length", 16));
        cfg.put("trainer", Map.of("runtime_parallel_num", 2));
        cfg.put("actor_rollout_ref", Map.of("rollout", Map.of("n", 2)));
        Map<String, Object> jiuwenRl = new HashMap<>();
        jiuwenRl.put("whole_trajectory", wholeTrajectory);
        jiuwenRl.put("custom_fn", Map.of(
                "classifier", "default_classify_rollouts",
                "validator", "default_validate_stop",
                "sampler", sampler
        ));
        jiuwenRl.put("final_keep_per_prompt", 8);
        cfg.put("JiuwenRL", jiuwenRl);
        return cfg;
    }

    private static RolloutWithReward rolloutWithReward(double reward) {
        RolloutWithReward rollout = new RolloutWithReward();
        rollout.setInputPromptIds(List.of(1, 2, 3));
        rollout.setOutputResponseIds(List.of(4, 5));
        rollout.setReward(reward);
        rollout.setNTurns(1);
        return rollout;
    }

    private static List<RolloutWithReward> mutableRollouts(int count, double reward) {
        List<RolloutWithReward> rollouts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rollouts.add(rolloutWithReward(reward));
        }
        return rollouts;
    }

    private static RolloutMessage rolloutMessage(String taskId, String originTaskId, double globalReward, int turns) {
        RolloutMessage message = new RolloutMessage();
        message.setTaskId(taskId);
        message.setOriginTaskId(originTaskId);
        message.setRolloutId("rollout-" + taskId);
        message.setGlobalReward(globalReward);
        message.setRewardList(List.of(globalReward, globalReward));
        List<Rollout> rolloutInfo = new ArrayList<>();
        for (int i = 0; i < turns; i++) {
            Rollout rollout = new Rollout();
            rollout.setTurnId(i);
            rollout.setInputPromptIds(List.of(i + 1, i + 2));
            rollout.setOutputResponseIds(List.of(i + 3, i + 4));
            rolloutInfo.add(rollout);
        }
        message.setRolloutInfo(rolloutInfo);
        return message;
    }

    private static final class TestTokenizer {
        public int getPadTokenId() {
            return 99;
        }
    }
}

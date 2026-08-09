/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.coordinator;

import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TrainingCoordinator} coordinator behavior in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/training_coordinator.py}.
 */
class TrainingCoordinatorTest {

    @Test
    void buildInitialTasksCreatesRolloutCopiesPerSample() {
        TrainingCoordinator coordinator = new TrainingCoordinator(config(), null);

        Map<String, RLTask> tasks = coordinator.buildInitialTasks(Map.of(
                "prompt", List.of("p0", "p1"),
                "answer", List.of("a0", "a1")
        ));

        assertEquals(4, tasks.size());
        Set<String> origins = tasks.values().stream()
                .map(RLTask::getOriginTaskId)
                .collect(Collectors.toSet());
        assertEquals(2, origins.size());
        assertTrue(tasks.values().stream().allMatch(task -> task.getRoundNum() == 0));
        assertTrue(tasks.values().stream().allMatch(task -> task.getTaskSample().containsKey("prompt")));
    }

    @Test
    void mergeCachesPreservesPositiveAndNegativeRolloutsByUid() {
        RolloutWithReward positive = rollout(1.0d);
        RolloutWithReward negative = rollout(-0.5d);

        Map<String, List<RolloutWithReward>> merged = TrainingCoordinator.mergeCaches(
                Map.of("uid", List.of(positive)),
                Map.of("uid", List.of(negative))
        );

        assertEquals(1, merged.size());
        assertEquals(List.of(positive, negative), merged.get("uid"));
    }

    @Test
    void clearUpDataResetsMutableCoordinatorState() {
        TrainingCoordinator coordinator = new TrainingCoordinator(config(), null);
        coordinator.getPositiveCache().put("uid", new ArrayList<>(List.of(rollout(1.0d))));
        coordinator.getNegativeCache().put("uid", new ArrayList<>(List.of(rollout(-0.1d))));

        coordinator.clearUpData();

        assertTrue(coordinator.getPositiveCache().isEmpty());
        assertTrue(coordinator.getNegativeCache().isEmpty());
        assertTrue(coordinator.getTurnCounts().isEmpty());
        assertEquals(0, coordinator.getTotalPositive());
        assertEquals(0, coordinator.getTotalNegative());
    }

    @Test
    void buildRlBatchFromCachesUsesConfiguredSamplerAndBatchBuilder() {
        TrainingCoordinator coordinator = new TrainingCoordinator(config(), null);
        coordinator.getPositiveCache().put("uid", new ArrayList<>(List.of(rollout(1.0d))));

        TrainingCoordinator.BatchBuildResult result = coordinator.buildRlBatchFromCaches(null);

        assertNotNull(result.rlBatch());
        assertFalse(result.mergedRollouts().isEmpty());
        assertEquals(1, result.rlBatch().batch().batchSize());
    }

    private static Map<String, Object> config() {
        return Map.of(
                "data", Map.of(
                        "max_prompt_length", 4,
                        "max_response_length", 4,
                        "pad_token_id", 0
                ),
                "actor_rollout_ref", Map.of(
                        "rollout", Map.of("n", 2)
                ),
                "trainer", Map.of(
                        "runtime_parallel_num", 1,
                        "rollout_max_round", 1
                ),
                "JiuwenRL", Map.of(
                        "whole_trajectory", false,
                        "custom_fn", Map.of(
                                "classifier", "default_classify_rollouts",
                                "validator", "validate_stop_balanced",
                                "sampler", "default_sampling"
                        )
                )
        );
    }

    private static RolloutWithReward rollout(double reward) {
        RolloutWithReward rollout = new RolloutWithReward();
        rollout.setInputPromptIds(List.of(1, 2));
        rollout.setOutputResponseIds(List.of(3, 4));
        rollout.setReward(reward);
        rollout.setTurnId(0);
        rollout.setNTurns(1);
        return rollout;
    }
}

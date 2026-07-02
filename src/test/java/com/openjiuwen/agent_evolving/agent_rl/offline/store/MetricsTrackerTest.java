/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's focused metrics-tracker coverage for
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/store/test_metrics_tracker.py}.
 */
@DisplayName("MetricsTracker Tests")
class MetricsTrackerTest {

    @Test
    void logStepAndFinishNoRaise() {
        RLMetricsTracker tracker = new RLMetricsTracker("p", "e", List.of("tensorboard"));

        tracker.logStep(0, Map.of("loss", 0.5d));
        tracker.finish();

        assertThat(tracker.isInitialized()).isTrue();
        assertThat(tracker.isFinished()).isTrue();
        assertThat(tracker.getLoggedSteps()).containsExactly(0);
        assertThat(tracker.getLoggedMetrics().get(0)).containsEntry("loss", 0.5d);
    }

    @Test
    void logTrainingStepAndLogRolloutStatsNoRaise() {
        RLMetricsTracker tracker = new RLMetricsTracker("p", "e", List.of("tensorboard"));

        tracker.logTrainingStep(new RLMetricsTracker.TrainingStepMetrics(
                0,
                0,
                Map.of("actor_loss", 0.1d),
                2.0d,
                0.5d,
                0));
        tracker.logRolloutStats(
                0,
                Map.of("u1", List.of(Map.of("global", 0.5d))),
                1,
                0);

        assertThat(tracker.getLoggedMetrics()).hasSize(2);
        assertThat(tracker.getLoggedMetrics().get(0))
                .containsEntry("actor_loss", 0.1d)
                .containsEntry("training/global_step", 0)
                .containsEntry("training/epoch", 0)
                .containsEntry("training/avg_conversation_turns", 2.0d)
                .containsEntry("training/rollout_reward_mean", 0.5d)
                .containsEntry("training/consecutive_zero_reward_steps", 0);
        assertThat(tracker.getLoggedMetrics().get(1))
                .containsEntry("rollout/reward_mean", 0.5d)
                .containsEntry("rollout/reward_std", 0.0d)
                .containsEntry("rollout/reward_max", 0.5d)
                .containsEntry("rollout/reward_min", 0.5d)
                .containsEntry("rollout/positive_ratio", 1.0d)
                .containsEntry("rollout/total_rollouts", 1)
                .containsEntry("rollout/unique_prompts", 1);
    }
}

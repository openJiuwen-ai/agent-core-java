/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.TrainingConfig;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.FileRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.NullRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RLMetricsTracker;
import com.openjiuwen.agent_evolving.agent_rl.optimizer.OfflineRLOptimizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RlOptimizer.
 * <p>
 * Mirrors Python's {@code test_rl_optimizer.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/optimizer/}.
 */
@DisplayName("RlOptimizer Tests")
class TestRlOptimizer {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("init run name contains experiment and timestamp")
    void testInitRunNameContainsExperimentAndTimestamp() {
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(minimalConfig());

        assertThat(optimizer.getRunName()).contains("exp_test");
        assertThat(optimizer.getRunName()).matches("exp_test_\\d{8}_\\d{6}");
    }

    @Test
    @DisplayName("build persistence disabled returns null store")
    void testBuildPersistenceDisabledReturnsNullStore() {
        RLConfig config = minimalConfig();
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(config);
        config.getPersistence().setEnabled(false);

        assertThat(optimizer.buildPersistence(config)).isInstanceOf(NullRolloutStore.class);
    }

    @Test
    @DisplayName("build persistence enabled returns file store")
    void testBuildPersistenceEnabledReturnsFileStore() {
        RLConfig config = minimalConfig();
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(config);
        config.getPersistence().setEnabled(true);
        config.getPersistence().setSavePath(tempDir.toString());

        assertThat(optimizer.buildPersistence(config)).isInstanceOf(FileRolloutStore.class);
    }

    @Test
    @DisplayName("build metrics tracker uses project and run name")
    void testBuildMetricsTrackerUsesProjectAndRunName() {
        RLConfig config = minimalConfig();
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(config);

        RLMetricsTracker tracker = optimizer.buildMetricsTracker(config);

        assertThat(tracker.getInitKwargs()).containsEntry("project_name", "proj");
        assertThat(tracker.getInitKwargs().get("experiment_name")).isEqualTo(optimizer.getRunName());
    }

    @Test
    @DisplayName("set tools and resolve agent factory returns factory")
    void testSetToolsAndResolveAgentFactoryReturnsFactory() {
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(minimalConfig());
        optimizer.setTools(List.of("tool1"));

        assertThat(optimizer.resolveAgentFactory()).isNotNull();
    }

    @Test
    @DisplayName("resolve agent factory custom takes precedence")
    void testResolveAgentFactoryCustomTakesPrecedence() {
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(minimalConfig());
        Object custom = new Object();

        optimizer.setAgentFactory(custom);

        assertThat(optimizer.resolveAgentFactory()).isSameAs(custom);
    }

    @Test
    @DisplayName("register reward empty name raises")
    void testRegisterRewardEmptyNameRaises() {
        OfflineRLOptimizer optimizer = new OfflineRLOptimizer(minimalConfig());

        assertThatThrownBy(() -> optimizer.registerReward(ignored -> 0, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RLConfig training required raises validation error")
    void testRlconfigTrainingRequiredRaisesValidationError() {
        assertThatThrownBy(() -> new OfflineRLOptimizer(new RLConfig(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("training");
    }

    private static RLConfig minimalConfig() {
        TrainingConfig training = new TrainingConfig();
        training.setExperimentName("exp_test");
        training.setProjectName("proj");
        return new RLConfig(training);
    }
}

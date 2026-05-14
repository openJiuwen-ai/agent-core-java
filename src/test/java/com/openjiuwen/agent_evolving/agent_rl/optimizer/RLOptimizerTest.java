/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.PersistenceConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.TrainingConfig;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.FileRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.NullRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RLMetricsTracker;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RLOptimizerTest {

    private RLConfig minimalConfig() {
        TrainingConfig training = new TrainingConfig();
        training.setExperimentName("exp_test");
        training.setProjectName("proj");
        return new RLConfig(training);
    }

    @Test
    void initRunNameContainsExperiment() {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        assertTrue(opt.getRunName().contains("exp_test"));
    }

    @Test
    void buildPersistenceDisabledReturnsNullStore() {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        opt.getConfig().getPersistence().setEnabled(false);
        Object store = opt.buildPersistence(opt.getConfig());
        assertInstanceOf(NullRolloutStore.class, store);
    }

    @Test
    void buildPersistenceEnabledReturnsFileStore() throws Exception {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        PersistenceConfig persistence = opt.getConfig().getPersistence();
        persistence.setEnabled(true);
        java.nio.file.Path tmp = Files.createTempDirectory("rl-store");
        persistence.setSavePath(tmp.toString());
        Object store = opt.buildPersistence(opt.getConfig());
        assertInstanceOf(FileRolloutStore.class, store);
        FileRolloutStore fileStore = (FileRolloutStore) store;
        assertEquals(tmp.resolve(opt.getRunName()), fileStore.getSavePath());
        assertTrue(Files.isDirectory(fileStore.getSavePath().resolve("train").resolve("rollouts")));
        assertTrue(Files.isDirectory(fileStore.getSavePath().resolve("val").resolve("rollouts")));
        assertTrue(Files.isDirectory(fileStore.getSavePath().resolve("step_summaries")));
    }

    @Test
    void buildMetricsTrackerUsesProjectAndRunName() {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        RLMetricsTracker tracker = opt.buildMetricsTracker(opt.getConfig());
        assertEquals("proj", tracker.getInitKwargs().get("project_name"));
        assertTrue(String.valueOf(tracker.getInitKwargs().get("experiment_name")).contains("exp_test"));
    }

    @Test
    void resolveAgentFactoryCustomTakesPrecedence() {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        Object custom = new Object();
        opt.setAgentFactory(custom);
        assertSame(custom, opt.resolveAgentFactory());
    }

    @Test
    void registerRewardEmptyNameRaises() {
        OfflineRLOptimizer opt = new OfflineRLOptimizer(minimalConfig());
        assertThrows(IllegalArgumentException.class, () -> opt.registerReward(x -> 0, ""));
    }

    @Test
    void rlConfigTrainingRequiredRaises() {
        RLConfig config = new RLConfig();
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void setupGatewayRejectsLegacyHttpGatewayUrl() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(minimalConfig());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> optimizer.setupGateway("http://127.0.0.1:18080/v1", 15.0, 8));
        assertTrue(ex.getMessage().contains("setup_gateway() no longer accepts HTTP Gateway URLs"));
        assertTrue(ex.getMessage().contains("setup_redis()"));
    }

    @Test
    void setupGatewayAcceptsRedisUrlForTransition() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(minimalConfig());
        OnlineRLOptimizer returned = optimizer.setupGateway("redis://127.0.0.1:6379/0", 15.0, 8);
        assertSame(optimizer, returned);
        assertEquals("redis://127.0.0.1:6379/0", optimizer.getRedisUrl());
        assertEquals(15.0, optimizer.getPollInterval());
        assertEquals(8, optimizer.getMinSamples());
    }
}

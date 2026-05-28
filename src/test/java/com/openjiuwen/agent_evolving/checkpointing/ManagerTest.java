/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for checkpointing types and managers via public API.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.checkpointing.test_manager}.
 */
class ManagerTest {

    // ========== Factory methods ==========

    private EvolveCheckpoint makeCheckpoint(Map<String, Object> overrides) {
        Map<String, Integer> step = new HashMap<>();
        step.put("epoch", 1);

        Map<String, Object> best = new HashMap<>();
        best.put("best_score", 0.5);

        return EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(step)
                .best(best)
                .seed(null)
                .operatorsState(new HashMap<>())
                .updaterState(new HashMap<>())
                .searcherState(new HashMap<>())
                .lastMetrics(new HashMap<>())
                .build();
    }

    private EvolveCheckpoint makeCheckpoint() {
        return makeCheckpoint(new HashMap<>());
    }

    // ========== TestEvolveCheckpoint tests ==========

    @Test
    void testEvolveCheckpointFullCreation() {
        // Create checkpoint with all fields
        Map<String, Integer> step = new HashMap<>();
        step.put("epoch", 5);
        step.put("batch", 100);

        Map<String, Object> best = new HashMap<>();
        best.put("best_score", 0.9);

        Map<String, Map<String, Object>> operatorsState = new HashMap<>();
        Map<String, Object> op1State = new HashMap<>();
        op1State.put("param", "value");
        operatorsState.put("op1", op1State);

        Map<String, Object> updaterState = new HashMap<>();
        updaterState.put("key", "value");

        Map<String, Object> lastMetrics = new HashMap<>();
        lastMetrics.put("score", 0.85);

        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(step)
                .best(best)
                .seed(42)
                .operatorsState(operatorsState)
                .updaterState(updaterState)
                .lastMetrics(lastMetrics)
                .searcherState(new HashMap<>())
                .build();

        assertEquals("v1", checkpoint.getVersion());
        assertEquals("test_run", checkpoint.getRunId());
        assertEquals(5, checkpoint.getStep().get("epoch"));
        assertEquals(0.9, checkpoint.getBest().get("best_score"));
        assertEquals(42, checkpoint.getSeed());
    }

    @Test
    void testEvolveCheckpointMinimalCreation() {
        // Create checkpoint with minimal fields
        EvolveCheckpoint checkpoint = makeCheckpoint();

        assertEquals("v1", checkpoint.getVersion());
        assertNull(checkpoint.getSeed());
    }

    @Test
    void testEvolveCheckpointSerialization() {
        // Checkpoint can be serialized to dict (access via getters)
        EvolveCheckpoint checkpoint = makeCheckpoint();

        assertEquals("v1", checkpoint.getVersion());
        assertEquals("test_run", checkpoint.getRunId());
    }

    // ========== TestDefaultCheckpointManager tests ==========

    @Test
    void testDefaultCheckpointManagerDefaultInit() {
        // Init with default values (verified through behavior)
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        assertNotNull(manager.getRunId());
        // save_every_n_epochs=1, save_on_improve=True by default
        // Behavior: should always save on epoch 0
        assertTrue(manager.shouldSave(0, false));
    }

    @Test
    void testDefaultCheckpointManagerCustomInit() {
        // Init with custom values (verified through behavior)
        DefaultCheckpointManager manager = new DefaultCheckpointManager(
                "custom_run",
                "v2",
                5,
                false
        );

        assertEquals("custom_run", manager.getRunId());
        // Verify save_every_n_epochs=5 through behavior
        assertTrue(manager.shouldSave(5, false));
        assertFalse(manager.shouldSave(1, false));
    }

    @Test
    void testShouldSaveOnImproveEnabled() {
        // Save when improved and save_on_improve is True
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 10, true);

        assertTrue(manager.shouldSave(0, true));
        assertTrue(manager.shouldSave(5, true));
        assertFalse(manager.shouldSave(5, false));
    }

    @Test
    void testShouldSaveOnImproveDisabled() {
        // Don't save on improve when save_on_improve is False
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 10, false);

        assertTrue(manager.shouldSave(0, true));
        assertTrue(manager.shouldSave(10, false));
    }

    @Test
    void testShouldSavePeriodic() {
        // Save every N epochs
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 3, false);

        assertTrue(manager.shouldSave(0, false));
        assertFalse(manager.shouldSave(1, false));
        assertTrue(manager.shouldSave(3, false));
    }

    @Test
    void testShouldSaveCombinedStrategy() {
        // Combined save on improve + every N epochs
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 5, true);

        assertTrue(manager.shouldSave(2, true));
        assertTrue(manager.shouldSave(5, false));
        assertFalse(manager.shouldSave(3, false));
    }

    @Test
    void testShouldSaveEveryNEpochsMinimumOne() {
        // save_every_n_epochs minimum is 1 (verified through should_save behavior)
        DefaultCheckpointManager managerZero = new DefaultCheckpointManager(null, "v1", 0, false);
        DefaultCheckpointManager managerOne = new DefaultCheckpointManager(null, "v1", 1, false);

        // Both should save on epoch 0
        assertTrue(managerZero.shouldSave(0, false));
        assertTrue(managerOne.shouldSave(0, false));

        // Both should save on epoch 1 (since minimum is 1)
        assertTrue(managerZero.shouldSave(1, false));
        assertTrue(managerOne.shouldSave(1, false));
    }
}
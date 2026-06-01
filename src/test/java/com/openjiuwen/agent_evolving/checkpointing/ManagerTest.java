/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for checkpointing types and managers via public API.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.checkpointing.test_manager}.
 */
class ManagerTest {

    // ========== Factory methods ==========

    @SuppressWarnings("unchecked")
    private EvolveCheckpoint makeCheckpoint(Map<String, Object> overrides) {
        Map<String, Integer> step = new HashMap<>();
        step.put("epoch", 1);

        Map<String, Object> best = new HashMap<>();
        best.put("best_score", 0.5);

        Map<String, Map<String, Object>> operatorsState = new HashMap<>();
        Map<String, Object> updaterState = new HashMap<>();
        Map<String, Object> searcherState = new HashMap<>();
        Map<String, Object> lastMetrics = new HashMap<>();

        return EvolveCheckpoint.builder()
                .version((String) overrides.getOrDefault("version", "v1"))
                .runId((String) overrides.getOrDefault("run_id", overrides.getOrDefault("runId", "test_run")))
                .step((Map<String, Integer>) overrides.getOrDefault("step", step))
                .best((Map<String, Object>) overrides.getOrDefault("best", best))
                .seed((Integer) overrides.getOrDefault("seed", null))
                .operatorsState((Map<String, Map<String, Object>>) overrides.getOrDefault("operators_state", operatorsState))
                .updaterState((Map<String, Object>) overrides.getOrDefault("updater_state", updaterState))
                .searcherState((Map<String, Object>) overrides.getOrDefault("searcher_state", searcherState))
                .lastMetrics((Map<String, Object>) overrides.getOrDefault("last_metrics", lastMetrics))
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

    @Test
    void testBuildCheckpointNoOperators() {
        FakeAgent agent = new FakeAgent(Map.of());
        FakeProgress progress = new FakeProgress(5, 100, 0.95, 42, 0.90);

        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);
        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, null);

        assertEquals("test_run", checkpoint.getRunId());
        assertEquals(5, checkpoint.getStep().get("epoch"));
        assertEquals(100, checkpoint.getStep().get("batch"));
        assertEquals(0.95, checkpoint.getBest().get("best_score"));
        assertEquals(42, checkpoint.getSeed());
        assertEquals(Map.of(), checkpoint.getOperatorsState());
    }

    @Test
    void testBuildCheckpointWithOperators() {
        FakeOperator op1 = new FakeOperator("llm_op", Map.of("system_prompt", "new prompt"));
        FakeOperator op2 = new FakeOperator("tool_op", Map.of("enabled", true));
        FakeAgent agent = new FakeAgent(Map.of("llm_op", op1, "tool_op", op2));
        FakeProgress progress = new FakeProgress(3, 50, 0.85, 123, 0.80);

        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);
        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, null);

        assertEquals("new prompt", checkpoint.getOperatorsState().get("llm_op").get("system_prompt"));
        assertEquals(true, checkpoint.getOperatorsState().get("tool_op").get("enabled"));
    }

    @Test
    void testBuildCheckpointWithUpdaterState() {
        FakeAgent agent = new FakeAgent(Map.of());
        FakeProgress progress = new FakeProgress(1, 10, 0.5, null, 0.6);

        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);
        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, Map.of("optimizier_step", 5));

        assertEquals(Map.of("optimizier_step", 5), checkpoint.getUpdaterState());
    }

    @Test
    void testBuildCheckpointAgentWithoutGetOperators() {
        Object agent = new Object();
        FakeProgress progress = new FakeProgress(2, 20, 0.6, null, 0.7);

        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);
        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, null);

        assertEquals(Map.of(), checkpoint.getOperatorsState());
    }

    @Test
    void testRestoreNoOperators() {
        FakeAgent agent = new FakeAgent(Map.of());
        EvolveCheckpoint checkpoint = makeCheckpoint(Map.of(
                "step", Map.of("epoch", 5, "batch", 100),
                "best", Map.of("best_score", 0.9),
                "seed", 42
        ));

        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        Map<String, Object> result = manager.restore(agent, checkpoint);

        assertEquals(5, result.get("start_epoch"));
        assertEquals(0.9, result.get("best_score"));
        assertEquals("test_run", result.get("run_id"));
    }

    @Test
    void testRestoreRestoresOperators() {
        FakeOperator op = new FakeOperator("llm_op", Map.of("param", "value"));
        FakeAgent agent = new FakeAgent(Map.of("llm_op", op));
        EvolveCheckpoint checkpoint = makeCheckpoint(Map.of(
                "step", Map.of("epoch", 3),
                "best", Map.of("best_score", 0.7),
                "seed", 42,
                "operators_state", Map.of("llm_op", Map.of("prompt", "restored_value"))
        ));

        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        Map<String, Object> result = manager.restore(agent, checkpoint);

        assertEquals(3, result.get("start_epoch"));
        assertEquals(Map.of("prompt", "restored_value"), op.loadedStates.getFirst());
    }

    @Test
    void testRestoreSkipsMissingOperators() {
        FakeOperator op = new FakeOperator("llm_op", Map.of("param", "value"));
        FakeAgent agent = new FakeAgent(Map.of("llm_op", op));
        EvolveCheckpoint checkpoint = makeCheckpoint(Map.of(
                "step", Map.of("epoch", 2),
                "best", Map.of("best_score", 0.6),
                "operators_state", Map.of("missing_op", Map.of())
        ));

        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        manager.restore(agent, checkpoint);

        assertEquals(List.of(), op.loadedStates);
    }

    @Test
    void testRestoreAgentWithoutGetOperators() {
        Object agent = new Object();
        EvolveCheckpoint checkpoint = makeCheckpoint(Map.of(
                "step", Map.of("epoch", 4),
                "best", Map.of("best_score", 0.8),
                "operators_state", Map.of("op1", Map.of("param", "value"))
        ));

        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        Map<String, Object> result = manager.restore(agent, checkpoint);

        assertEquals(4, result.get("start_epoch"));
    }

    @Test
    void testRestoreReturnsProgressState() {
        EvolveCheckpoint checkpoint = makeCheckpoint(Map.of(
                "step", Map.of("epoch", 5, "batch", 100),
                "best", Map.of("best_score", 0.9),
                "seed", 42
        ));
        FakeAgent agent = new FakeAgent(Map.of());

        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        Map<String, Object> result = manager.restore(agent, checkpoint);

        assertEquals(5, result.get("start_epoch"));
        assertEquals(0.9, result.get("best_score"));
        assertEquals("test_run", result.get("run_id"));
    }

    static final class FakeAgent {
        private final Map<String, Object> operators;

        FakeAgent(Map<String, Object> operators) {
            this.operators = operators;
        }

        public Map<String, Object> getOperators() {
            return operators;
        }
    }

    static final class FakeOperator {
        private final String operatorId;
        private final Map<String, Object> state;
        private final List<Map<String, Object>> loadedStates = new java.util.ArrayList<>();

        FakeOperator(String operatorId, Map<String, Object> state) {
            this.operatorId = operatorId;
            this.state = new LinkedHashMap<>(state);
        }

        public String getOperatorId() {
            return operatorId;
        }

        public Map<String, Object> getState() {
            return new LinkedHashMap<>(state);
        }

        public void loadState(Map<String, Object> state) {
            loadedStates.add(new LinkedHashMap<>(state));
        }
    }

    static final class FakeProgress {
        private final int currentEpoch;
        private final int currentBatchIter;
        private final double bestScore;
        private final Integer seed;
        private final double currentEpochScore;

        FakeProgress(int currentEpoch, int currentBatchIter, double bestScore, Integer seed, double currentEpochScore) {
            this.currentEpoch = currentEpoch;
            this.currentBatchIter = currentBatchIter;
            this.bestScore = bestScore;
            this.seed = seed;
            this.currentEpochScore = currentEpochScore;
        }

        public int getCurrentEpoch() {
            return currentEpoch;
        }

        public int getCurrentBatchIter() {
            return currentBatchIter;
        }

        public double getBestScore() {
            return bestScore;
        }

        public Integer getSeed() {
            return seed;
        }

        public double getCurrentEpochScore() {
            return currentEpochScore;
        }
    }
}

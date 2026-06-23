/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.experience.PendingChange;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the default checkpoint manager.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/checkpointing/test_manager.py}.</p>
 *
 * <p>Mirrors Python's {@code DefaultCheckpointManager} in
 * {@code openjiuwen/agent_evolving/checkpointing/manager.py}.</p>
 */
class DefaultCheckpointManagerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void evolveCheckpointFullCreation() {
        EvolveCheckpoint checkpoint = makeCheckpoint(
                Map.of("epoch", 5, "batch", 100),
                Map.of("best_score", 0.9d),
                42,
                Map.of("op1", Map.of("param", "value")),
                Map.of("key", "value"),
                Map.of("score", 0.85d));

        assertEquals("v1", checkpoint.getVersion());
        assertEquals("test_run", checkpoint.getRunId());
        assertEquals(5, checkpoint.getStep().get("epoch"));
        assertEquals(0.9d, (Double) checkpoint.getBest().get("best_score"), 1.0e-9);
        assertEquals(42, checkpoint.getSeed());
    }

    @Test
    void evolveCheckpointMinimalCreationAllowsNullSeed() {
        EvolveCheckpoint checkpoint = makeCheckpoint(
                Map.of("epoch", 1),
                Map.of("best_score", 0.5d),
                null,
                Map.of(),
                Map.of(),
                Map.of());

        assertEquals("v1", checkpoint.getVersion());
        assertNull(checkpoint.getSeed());
    }

    @Test
    void evolveCheckpointSerializesToPythonDictShape() throws Exception {
        EvolveCheckpoint checkpoint = makeCheckpoint(
                Map.of("epoch", 1),
                Map.of("best_score", 0.5d),
                null,
                Map.of(),
                Map.of(),
                Map.of());

        Map<String, Object> data = OBJECT_MAPPER.readValue(
                OBJECT_MAPPER.writeValueAsString(checkpoint),
                new TypeReference<>() {
                });

        assertEquals("v1", data.get("version"));
        assertEquals("test_run", data.get("run_id"));
    }

    @Test
    void defaultInitUsesGeneratedRunIdAndSavesEpochZero() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        assertNotNull(manager.getRunId());
        assertTrue(manager.shouldSave(0, false));
    }

    @Test
    void customInitUsesConfiguredRunIdAndPeriodicRule() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("custom_run", "v2", 5, false);

        assertEquals("custom_run", manager.getRunId());
        assertTrue(manager.shouldSave(5, false));
        assertFalse(manager.shouldSave(1, false));
    }

    @Test
    void shouldSaveOnImproveEnabled() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 10, true);

        assertTrue(manager.shouldSave(0, true));
        assertTrue(manager.shouldSave(5, true));
        assertFalse(manager.shouldSave(5, false));
    }

    @Test
    void shouldSaveOnImproveDisabledStillUsesPeriodicRule() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 10, false);

        assertTrue(manager.shouldSave(0, true));
        assertTrue(manager.shouldSave(10, false));
        assertFalse(manager.shouldSave(5, true));
    }

    @Test
    void shouldSavePeriodic() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 3, false);

        assertTrue(manager.shouldSave(0, false));
        assertFalse(manager.shouldSave(1, false));
        assertTrue(manager.shouldSave(3, false));
    }

    @Test
    void shouldSaveMatchesImproveAndPeriodicRules() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager(null, "v1", 5, true);

        assertTrue(manager.shouldSave(2, true));
        assertTrue(manager.shouldSave(5, false));
        assertFalse(manager.shouldSave(3, false));
    }

    @Test
    void saveEveryNEpochsMinimumIsOne() {
        DefaultCheckpointManager managerZero = new DefaultCheckpointManager(null, "v1", 0, false);
        DefaultCheckpointManager managerOne = new DefaultCheckpointManager(null, "v1", 1, false);

        assertTrue(managerZero.shouldSave(0, false));
        assertTrue(managerOne.shouldSave(0, false));
        assertTrue(managerZero.shouldSave(1, false));
        assertTrue(managerOne.shouldSave(1, false));
    }

    @Test
    void buildCheckpointNoOperators() {
        FakeAgent agent = new FakeAgent();
        FakeProgress progress = new FakeProgress();
        progress.current_epoch = 5;
        progress.currentBatchIter = 100;
        progress.best_score = 0.95d;
        progress.seed = 42;
        progress.currentEpochScore = 0.90d;
        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, null);

        assertEquals("test_run", checkpoint.getRunId());
        assertEquals(5, checkpoint.getStep().get("epoch"));
        assertEquals(0.95d, (Double) checkpoint.getBest().get("best_score"), 1.0e-9);
        assertEquals(42, checkpoint.getSeed());
        assertTrue(checkpoint.getOperatorsState().isEmpty());
    }

    @Test
    void buildCheckpointSnapshotsOperatorsAndProgress() {
        FakeOperator first = new FakeOperator("llm_op", Map.of("system_prompt", "new prompt"));
        FakeOperator second = new FakeOperator("tool_op", Map.of("enabled", true));
        FakeAgent agent = new FakeAgent(first, second);
        FakeProgress progress = new FakeProgress();
        progress.current_epoch = 3;
        progress.currentBatchIter = 50;
        progress.best_score = 0.85d;
        progress.seed = 123;
        progress.currentEpochScore = 0.80d;
        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v2", 1, true);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, Map.of("optimizer_step", 5));

        assertEquals("v2", checkpoint.getVersion());
        assertEquals("test_run", checkpoint.getRunId());
        assertEquals(3, checkpoint.getStep().get("epoch"));
        assertEquals(50, checkpoint.getStep().get("batch"));
        assertEquals(0.85d, (Double) checkpoint.getBest().get("best_score"), 1.0e-9);
        assertEquals(123, checkpoint.getSeed());
        assertEquals("new prompt", checkpoint.getOperatorsState().get("llm_op").get("system_prompt"));
        assertEquals(true, checkpoint.getOperatorsState().get("tool_op").get("enabled"));
        assertEquals(5, checkpoint.getUpdaterState().get("optimizer_step"));
        assertEquals(0.80d, (Double) checkpoint.getLastMetrics().get("current_epoch_score"), 1.0e-9);
    }

    @Test
    void buildCheckpointWithUpdaterState() {
        FakeAgent agent = new FakeAgent();
        FakeProgress progress = new FakeProgress();
        progress.current_epoch = 1;
        progress.currentBatchIter = 10;
        progress.best_score = 0.5d;
        progress.currentEpochScore = 0.6d;
        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, Map.of("optimizier_step", 5));

        assertEquals(Map.of("optimizier_step", 5), checkpoint.getUpdaterState());
    }

    @Test
    void buildCheckpointAgentWithoutGetOperators() {
        FakeProgress progress = new FakeProgress();
        progress.current_epoch = 2;
        progress.currentBatchIter = 20;
        progress.best_score = 0.6d;
        progress.currentEpochScore = 0.7d;
        DefaultCheckpointManager manager = new DefaultCheckpointManager("test_run", "v1", 1, true);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(new NoOperatorsAgent(), progress, null);

        assertTrue(checkpoint.getOperatorsState().isEmpty());
    }

    @Test
    void restoreNoOperators() {
        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(Map.of("epoch", 5, "batch", 100))
                .best(Map.of("best_score", 0.9d))
                .seed(42)
                .operatorsState(Map.of())
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        Map<String, Object> progress = manager.restore(new FakeAgent(), checkpoint);

        assertEquals(5, progress.get("start_epoch"));
        assertEquals(0.9d, (Double) progress.get("best_score"), 1.0e-9);
        assertEquals("test_run", progress.get("run_id"));
    }

    @Test
    void restoreLoadsOperatorsAndReturnsPythonProgressKeys() {
        FakeOperator operator = new FakeOperator("llm_op", Map.of());
        FakeAgent agent = new FakeAgent(operator);
        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(Map.of("epoch", 5, "batch", 100))
                .best(Map.of("best_score", 0.9d))
                .operatorsState(Map.of("llm_op", Map.of("prompt", "restored")))
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        Map<String, Object> progress = manager.restore(agent, checkpoint);

        assertEquals(Map.of("prompt", "restored"), operator.loadedState);
        assertEquals(5, progress.get("start_epoch"));
        assertEquals(0.9d, (Double) progress.get("best_score"), 1.0e-9);
        assertEquals("test_run", progress.get("run_id"));
        assertFalse(progress.containsKey("startEpoch"));
    }

    @Test
    void restoreSkipsMissingOperators() {
        FakeOperator operator = new FakeOperator("llm_op", Map.of());
        FakeAgent agent = new FakeAgent(operator);
        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(Map.of("epoch", 2))
                .best(Map.of("best_score", 0.6d))
                .operatorsState(Map.of("missing_op", Map.of()))
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        manager.restore(agent, checkpoint);

        assertNull(operator.loadedState);
    }

    @Test
    void restoreAgentWithoutGetOperators() {
        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(Map.of("epoch", 4))
                .best(Map.of("best_score", 0.8d))
                .operatorsState(Map.of("op1", Map.of("param", "value")))
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        Map<String, Object> progress = manager.restore(new NoOperatorsAgent(), checkpoint);

        assertEquals(4, progress.get("start_epoch"));
    }

    @Test
    void pendingChangesDrainAndDiscardByOperator() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        PendingChange first = new PendingChange();
        first.setChangeId("change-1");
        first.setPayload(List.of(new EvolutionRecord(), new EvolutionRecord()));
        PendingChange second = new PendingChange();
        second.setChangeId("change-2");
        second.setPayload(List.of(new EvolutionRecord()));

        manager.addPending("op", first);
        manager.addPending("op", second);
        manager.discardPending("op", "change-1");
        int committed = manager.commitPending("op", null);

        assertEquals(1, committed);
        assertTrue(manager.getPending("op").isEmpty());
        assertNotNull(manager.getRunId());
    }

    @Test
    void discardPendingMatchesPythonNoneChangeId() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager();
        PendingChange change = new PendingChange();
        change.setChangeId(null);
        change.setPayload(List.of(new EvolutionRecord()));

        manager.addPending("op", change);
        manager.discardPending("op", null);

        assertTrue(manager.getPending("op").isEmpty());
    }

    @Test
    void restoreRejectsNullCheckpointLikePythonAttributeAccess() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager();

        assertThrows(NullPointerException.class, () -> manager.restore(new Object(), null));
    }

    private static final class FakeAgent {
        private final Map<String, Object> operators = new LinkedHashMap<>();

        private FakeAgent(FakeOperator... operators) {
            for (FakeOperator operator : operators) {
                this.operators.put(operator.operator_id, operator);
            }
        }

        @SuppressWarnings("unused")
        public Map<String, Object> get_operators() {
            return operators;
        }
    }

    private static final class FakeOperator {
        private final String operator_id;
        private final Map<String, Object> state;
        private Map<String, Object> loadedState;

        private FakeOperator(String operatorId, Map<String, Object> state) {
            this.operator_id = operatorId;
            this.state = state;
        }

        @SuppressWarnings("unused")
        public Map<String, Object> get_state() {
            return state;
        }

        @SuppressWarnings("unused")
        public void load_state(Map<String, Object> state) {
            this.loadedState = state;
        }
    }

    private static final class FakeProgress {
        private int current_epoch;
        private int currentBatchIter;
        private double best_score;
        private Integer seed;
        private double currentEpochScore;
    }

    private static final class NoOperatorsAgent {
    }

    private static EvolveCheckpoint makeCheckpoint(
            Map<String, Integer> step,
            Map<String, Object> best,
            Integer seed,
            Map<String, Map<String, Object>> operatorsState,
            Map<String, Object> updaterState,
            Map<String, Object> lastMetrics) {
        return EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(step)
                .best(best)
                .seed(seed)
                .operatorsState(operatorsState)
                .updaterState(updaterState)
                .searcherState(Map.of())
                .lastMetrics(lastMetrics)
                .build();
    }
}

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCheckpointManagerTest {

    @Test
    void shouldSaveHonorsImproveAndPeriodicStrategy() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_1", "v1", 3, true);

        assertTrue(manager.shouldSave(2, true));
        assertTrue(manager.shouldSave(3, false));
    }

    @Test
    void constructorTreatsEmptyRunIdAsMissingLikePython() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("", "v1", 1, true);

        assertTrue(manager.getRunId() != null && !manager.getRunId().isEmpty());
    }

    @Test
    void buildCheckpointUsesSnakeCaseMetricKeys() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_2", "v1", 1, true);
        FakeAgent agent = new FakeAgent(Map.of(
                "op_a", new FakeOperator("op_a", Map.of("prompt", "value"))
        ));
        FakeProgress progress = new FakeProgress(5, 12, 0.85, 123, 0.8);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(agent, progress, Map.of("step", 7));

        assertEquals("run_2", checkpoint.getRunId());
        assertEquals(5, checkpoint.getStep().get("epoch"));
        assertEquals(12, checkpoint.getStep().get("batch"));
        assertEquals(0.85, checkpoint.getBest().get("best_score"));
        assertEquals(0.8, checkpoint.getLastMetrics().get("current_epoch_score"));
        assertEquals("value", checkpoint.getOperatorsState().get("op_a").get("prompt"));
        assertEquals(7, checkpoint.getUpdaterState().get("step"));
    }

    @Test
    void buildCheckpointCoercesNumericStringProgressValues() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_strings", "v1", 1, true);

        EvolveCheckpoint checkpoint = manager.buildCheckpoint(
                new FakeAgent(Map.of()),
                new FakeStringProgress("6", "14", "0.88", "0.81"),
                Map.of()
        );

        assertEquals(6, checkpoint.getStep().get("epoch"));
        assertEquals(14, checkpoint.getStep().get("batch"));
        assertEquals(0.88, checkpoint.getBest().get("best_score"));
        assertEquals(0.81, checkpoint.getLastMetrics().get("current_epoch_score"));
    }

    @Test
    void restoreLoadsOperatorStateAndReturnsCompatibleProgressKeys() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_3", "v1", 1, true);
        FakeOperator operator = new FakeOperator("op_restore", Map.of("prompt", "before"));
        FakeAgent agent = new FakeAgent(Map.of("op_restore", operator));

        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("run_restore")
                .step(Map.of("epoch", 4, "batch", 10))
                .best(Map.of("best_score", 0.91))
                .seed(42)
                .operatorsState(Map.of("op_restore", Map.of("prompt", "after")))
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();

        Map<String, Object> restored = manager.restore(agent, checkpoint);

        assertEquals("after", operator.state.get("prompt"));
        assertEquals(4, restored.get("start_epoch"));
        assertEquals(4, restored.get("startEpoch"));
        assertEquals(0.91, restored.get("best_score"));
        assertEquals(0.91, restored.get("bestScore"));
        assertEquals("run_restore", restored.get("run_id"));
        assertEquals("run_restore", restored.get("runId"));
    }

    @Test
    void restoreAcceptsLegacyBestScoreKey() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_4", "v1", 1, true);
        FakeAgent agent = new FakeAgent(Map.of());

        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("run_legacy")
                .step(Map.of("epoch", 2))
                .best(new LinkedHashMap<>(Map.of("bestScore", "0.72")))
                .seed(null)
                .operatorsState(Map.of())
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();

        Map<String, Object> restored = manager.restore(agent, checkpoint);

        assertEquals(0.72, restored.get("best_score"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreCoercesNumericStringEpochValues() {
        DefaultCheckpointManager manager = new DefaultCheckpointManager("run_5", "v1", 1, true);
        FakeAgent agent = new FakeAgent(Map.of());
        Map<String, Integer> step = (Map<String, Integer>) (Map<?, ?>) new LinkedHashMap<>(Map.of("epoch", "7"));

        EvolveCheckpoint checkpoint = EvolveCheckpoint.builder()
                .version("v1")
                .runId("run_string_epoch")
                .step(step)
                .best(Map.of("best_score", 0.5))
                .seed(null)
                .operatorsState(Map.of())
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();

        Map<String, Object> restored = manager.restore(agent, checkpoint);

        assertEquals(7, restored.get("start_epoch"));
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
        private Map<String, Object> state;

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
            this.state = new LinkedHashMap<>(state);
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

    static final class FakeStringProgress {
        private final String currentEpoch;
        private final String currentBatchIter;
        private final String bestScore;
        private final String currentEpochScore;

        FakeStringProgress(String currentEpoch, String currentBatchIter, String bestScore, String currentEpochScore) {
            this.currentEpoch = currentEpoch;
            this.currentBatchIter = currentBatchIter;
            this.bestScore = bestScore;
            this.currentEpochScore = currentEpochScore;
        }

        public String getCurrentEpoch() {
            return currentEpoch;
        }

        public String getCurrentBatchIter() {
            return currentBatchIter;
        }

        public String getBestScore() {
            return bestScore;
        }

        public String getCurrentEpochScore() {
            return currentEpochScore;
        }
    }
}

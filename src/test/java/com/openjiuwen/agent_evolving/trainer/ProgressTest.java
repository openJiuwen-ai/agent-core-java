package com.openjiuwen.agent_evolving.trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/trainer/test_progress.py}.
 */
class ProgressTest {

    @Test
    void defaultConstructorUsesPythonDefaults() {
        Progress progress = new Progress();

        assertEquals(0, progress.getStartEpoch());
        assertEquals(0, progress.getCurrentEpoch());
        assertEquals(3, progress.getMaxEpoch());
        assertEquals(0.0, progress.getBestScore());
        assertEquals(0.0, progress.getCurrentEpochScore());
    }

    @Test
    void seedRoundTripsForCompatibility() {
        Progress progress = new Progress();

        progress.setSeed(123);

        assertEquals(123, progress.getSeed());
    }

    @Test
    void customConstructorUsesCustomMaxEpoch() {
        Progress progress = new Progress(10);
        progress.setBestScore(0.85);

        assertEquals(10, progress.getMaxEpoch());
        assertEquals(0.85, progress.getBestScore());
    }

    @Test
    void runEpochYieldsAndUpdatesCurrentEpoch() {
        Progress progress = new Progress(3);

        List<Integer> epochs = new ArrayList<>();
        for (int epoch : progress.runEpoch()) {
            epochs.add(epoch);
        }

        assertEquals(List.of(1, 2, 3), epochs);
        assertEquals(3, progress.getCurrentEpoch());
    }

    @Test
    void runEpochRespectsNonZeroStartEpoch() {
        Progress progress = new Progress(5);
        progress.setStartEpoch(2);

        List<Integer> epochs = new ArrayList<>();
        for (int epoch : progress.runEpoch()) {
            epochs.add(epoch);
        }

        assertEquals(List.of(3, 4, 5), epochs);
        assertEquals(5, progress.getCurrentEpoch());
    }

    @Test
    void runEpochRespectsStartEpochAndEmptyIteration() {
        Progress progress = new Progress(5);
        progress.setStartEpoch(5);

        List<Integer> epochs = new ArrayList<>();
        for (int epoch : progress.runEpoch()) {
            epochs.add(epoch);
        }

        assertEquals(List.of(), epochs);
        assertEquals(5, progress.getCurrentEpoch());
    }

    @Test
    void runEpochNoIterationsWhenStartEqualsMax() {
        Progress progress = new Progress(5);
        progress.setStartEpoch(5);

        assertEquals(List.of(), toList(progress.runEpoch()));
        assertEquals(5, progress.getCurrentEpoch());
    }

    @Test
    void runEpochSupportsPartialIteration() {
        Progress progress = new Progress(4);
        Iterator<Integer> iterator = progress.runEpoch().iterator();

        assertEquals(1, iterator.next());
        assertEquals(1, progress.getCurrentEpoch());
        assertEquals(2, iterator.next());
        assertEquals(2, progress.getCurrentEpoch());
    }

    @Test
    void runBatchResetsBestBatchScoreAndTracksIteration() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(3);
        progress.setBestBatchScore(0.8);

        List<Integer> batches = new ArrayList<>();
        for (int batch : progress.runBatch()) {
            batches.add(batch);
        }

        assertEquals(List.of(0, 1, 2), batches);
        assertEquals(2, progress.getCurrentBatchIter());
        assertEquals(0.0, progress.getBestBatchScore());
    }

    @Test
    void runBatchSingleIteration() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(1);

        assertEquals(List.of(0), toList(progress.runBatch()));
    }

    @Test
    void runBatchNoIterationsWhenMaxBatchIterIsZero() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(0);

        assertEquals(List.of(), toList(progress.runBatch()));
    }

    @Test
    void scoreRangeAcceptsValidScore() {
        Progress progress = new Progress();
        progress.setBestScore(0.5);

        assertEquals(0.5, progress.getBestScore());
    }

    @Test
    void epochRangeAcceptsNonNegativeStartEpoch() {
        Progress progress = new Progress(10);
        progress.setStartEpoch(1);

        assertEquals(1, progress.getStartEpoch());
        assertEquals(10, progress.getMaxEpoch());
    }

    @Test
    void settersClampScoresAndNormalizeBounds() {
        Progress progress = new Progress();
        progress.setStartEpoch(-1);
        progress.setCurrentEpoch(-1);
        progress.setCurrentBatchIter(-3);
        progress.setMaxBatchIter(-5);
        progress.setBestScore(1.5);
        progress.setBestBatchScore(-0.2);
        progress.setCurrentEpochScore(2.0);

        assertEquals(0, progress.getStartEpoch());
        assertEquals(0, progress.getCurrentEpoch());
        assertEquals(0, progress.getCurrentBatchIter());
        assertEquals(0, progress.getMaxBatchIter());
        assertEquals(1.0, progress.getBestScore());
        assertEquals(0.0, progress.getBestBatchScore());
        assertEquals(1.0, progress.getCurrentEpochScore());
    }

    @Test
    void callbacksConstructorIsNoop() {
        assertDoesNotThrow(Callbacks::new);
    }

    @Test
    void callbacksDefaultHooksAreNoops() {
        Callbacks callbacks = new Callbacks();
        Progress progress = new Progress();
        Object agent = new Object();
        List<com.openjiuwen.agent_evolving.dataset.EvaluatedCase> evalInfo = List.of();

        assertDoesNotThrow(() -> callbacks.onTrainBegin(agent, progress, evalInfo));
        assertDoesNotThrow(() -> callbacks.onTrainEnd(agent, progress, evalInfo));
        assertDoesNotThrow(() -> callbacks.onTrainEpochBegin(agent, progress));
        assertDoesNotThrow(() -> callbacks.onTrainEpochEnd(agent, progress, evalInfo));
    }

    @Test
    void callbacksCanBeSubclassedAndOverridden() {
        List<String> calls = new ArrayList<>();
        Callbacks callbacks = new Callbacks() {
            @Override
            public void onTrainBegin(Object agent, Progress progress,
                                     List<com.openjiuwen.agent_evolving.dataset.EvaluatedCase> evalInfo) {
                calls.add("begin");
            }

            @Override
            public void onTrainEpochBegin(Object agent, Progress progress) {
                calls.add("epoch_begin");
            }
        };

        callbacks.onTrainBegin(new Object(), new Progress(), List.of());
        callbacks.onTrainEpochBegin(new Object(), new Progress());

        assertEquals(List.of("begin", "epoch_begin"), calls);
    }

    private static List<Integer> toList(Iterable<Integer> values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) {
            result.add(value);
        }
        return result;
    }
}

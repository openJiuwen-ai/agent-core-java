package com.openjiuwen.agent_evolving.trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

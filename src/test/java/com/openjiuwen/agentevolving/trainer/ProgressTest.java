package com.openjiuwen.agentevolving.trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressTest {

    @Test
    void runEpochYieldsAndUpdatesCurrentEpoch() {
        Progress progress = new Progress();
        progress.setMaxEpoch(3);

        List<Integer> epochs = new ArrayList<>();
        for (int epoch : progress.runEpoch()) {
            epochs.add(epoch);
        }

        assertEquals(List.of(1, 2, 3), epochs);
        assertEquals(3, progress.getCurrentEpoch());
    }

    @Test
    void runEpochRespectsStartEpochAndEmptyIteration() {
        Progress progress = new Progress();
        progress.setMaxEpoch(5);
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
        Progress progress = new Progress();
        progress.setMaxEpoch(4);
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
    void settersRejectInvalidValues() {
        Progress progress = new Progress();

        // Negative integer fields throw
        assertThrows(IllegalArgumentException.class, () -> progress.setStartEpoch(-1));
        assertThrows(IllegalArgumentException.class, () -> progress.setCurrentEpoch(-1));
        assertThrows(IllegalArgumentException.class, () -> progress.setCurrentBatchIter(-3));
        assertThrows(IllegalArgumentException.class, () -> progress.setMaxBatchIter(-5));

        // Out-of-range score fields throw
        assertThrows(IllegalArgumentException.class, () -> progress.setBestScore(1.5));
        assertThrows(IllegalArgumentException.class, () -> progress.setBestBatchScore(-0.2));
        assertThrows(IllegalArgumentException.class, () -> progress.setCurrentEpochScore(2.0));

        // Valid values are accepted
        progress.setStartEpoch(0);
        progress.setCurrentEpoch(0);
        progress.setCurrentBatchIter(0);
        progress.setMaxBatchIter(0);
        progress.setBestScore(1.0);
        progress.setBestBatchScore(0.0);
        progress.setCurrentEpochScore(1.0);

        assertEquals(0, progress.getStartEpoch());
        assertEquals(0, progress.getCurrentEpoch());
        assertEquals(0, progress.getCurrentBatchIter());
        assertEquals(0, progress.getMaxBatchIter());
        assertEquals(1.0, progress.getBestScore());
        assertEquals(0.0, progress.getBestBatchScore());
        assertEquals(1.0, progress.getCurrentEpochScore());
    }
}

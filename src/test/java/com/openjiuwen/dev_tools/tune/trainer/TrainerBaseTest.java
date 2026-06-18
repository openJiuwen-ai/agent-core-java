/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for trainer base types.
 *
 * <p>Mirrors Python's {@code Progress} and {@code Callbacks} in
 * {@code openjiuwen/dev_tools/tune/trainer/base.py}.</p>
 */
class TrainerBaseTest {

    @Test
    void progressDefaultsMatchPythonModel() {
        Progress progress = new Progress();

        assertEquals(0, progress.getCurrentEpoch());
        assertEquals(3, progress.getMaxEpoch());
        assertEquals(0, progress.getCurrentBatchIter());
        assertEquals(1, progress.getMaxBatchIter());
        assertEquals(0.0d, progress.getBestScore());
        assertEquals(0.0d, progress.getBestBatchScore());
        assertEquals(0.0d, progress.getCurrentEpochScore());
    }

    @Test
    void runEpochYieldsOneBasedEpochsAndUpdatesCurrentEpoch() {
        Progress progress = new Progress();
        progress.setMaxEpoch(3);

        assertEquals(List.of(1, 2, 3), toList(progress.runEpoch()));
        assertEquals(3, progress.getCurrentEpoch());
    }

    @Test
    void runBatchYieldsZeroBasedIterationsAndResetsBestBatchScore() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(3);
        progress.setBestBatchScore(0.75d);

        assertEquals(List.of(0, 1, 2), toList(progress.runBatch()));
        assertEquals(2, progress.getCurrentBatchIter());
        assertEquals(0.0d, progress.getBestBatchScore());
    }

    @Test
    void pydanticBoundsAreEnforced() {
        Progress progress = new Progress();

        assertThrows(IllegalArgumentException.class, () -> progress.setMaxEpoch(-1));
        assertThrows(IllegalArgumentException.class, () -> progress.setBestScore(1.1d));
        assertDoesNotThrow(() -> progress.setCurrentEpochScore(1.0d));
    }

    @Test
    void callbacksAreNoOpsAndCanBeOverridden() {
        Progress progress = new Progress();
        Callbacks callbacks = new Callbacks();

        assertDoesNotThrow(() -> {
            callbacks.onTrainBegin(null, progress, List.of());
            callbacks.onTrainEpochBegin(null, progress);
            callbacks.onTrainEpochEnd(null, progress, List.of());
            callbacks.onTrainEnd(null, progress, List.of());
        });

        class TrackingCallbacks extends Callbacks {
            private int calls;

            @Override
            public void onTrainEpochBegin(com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent agent,
                                          Progress progress) {
                calls += 1;
            }
        }

        TrackingCallbacks tracking = new TrackingCallbacks();
        tracking.onTrainEpochBegin(null, progress);
        assertEquals(1, tracking.calls);
    }

    private static List<Integer> toList(Iterable<Integer> iterable) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        return values;
    }
}

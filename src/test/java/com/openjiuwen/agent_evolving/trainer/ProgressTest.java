/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code Progress} and {@code Callbacks} in
 * {@code openjiuwen/agent_evolving/trainer/progress.py}.
 */
class ProgressTest {

    @Test
    void runEpochYieldsEpochsAndUpdatesCurrentEpoch() {
        Progress progress = new Progress();
        progress.setMaxEpoch(3);

        assertThat(toList(progress.runEpoch())).containsExactly(1, 2, 3);
        assertThat(progress.getCurrentEpoch()).isEqualTo(3);
    }

    @Test
    void runEpochRespectsStartEpochAndKeepsInterruptedState() {
        Progress progress = new Progress();
        progress.setStartEpoch(2);
        progress.setMaxEpoch(5);

        var iterator = progress.runEpoch().iterator();

        assertThat(iterator.next()).isEqualTo(3);
        assertThat(progress.getCurrentEpoch()).isEqualTo(3);
    }

    @Test
    void runEpochFinalizesCurrentEpochWhenNoIterationsRun() {
        Progress progress = new Progress();
        progress.setStartEpoch(3);
        progress.setMaxEpoch(2);

        assertThat(toList(progress.runEpoch())).isEmpty();
        assertThat(progress.getCurrentEpoch()).isEqualTo(2);
    }

    @Test
    void runBatchYieldsZeroBasedIterationsAndResetsBestBatchScore() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(3);
        progress.setBestBatchScore(0.75d);

        assertThat(toList(progress.runBatch())).containsExactly(0, 1, 2);
        assertThat(progress.getCurrentBatchIter()).isEqualTo(2);
        assertThat(progress.getBestBatchScore()).isZero();
    }

    @Test
    void runBatchAllowsNoIterations() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(0);
        progress.setBestBatchScore(0.5d);

        assertThat(toList(progress.runBatch())).isEmpty();
        assertThat(progress.getBestBatchScore()).isZero();
    }

    @Test
    void pydanticFieldBoundsAreEnforced() {
        Progress progress = new Progress();

        assertThatThrownBy(() -> progress.setMaxEpoch(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> progress.setBestScore(1.1d)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> progress.setCurrentEpochScore(1.0d)).doesNotThrowAnyException();
    }

    @Test
    void defaultCallbacksAreNoOpsAndCanBeOverridden() {
        Callbacks callbacks = new Callbacks();
        Progress progress = new Progress();

        assertThatCode(() -> {
            callbacks.onTrainBegin(null, progress, List.of());
            callbacks.onTrainEpochBegin(null, progress);
            callbacks.onTrainEpochEnd(null, progress, List.of());
            callbacks.onTrainEnd(null, progress, List.of());
        }).doesNotThrowAnyException();

        class TrackingCallbacks extends Callbacks {
            private int calls;

            @Override
            public void onTrainEpochBegin(com.openjiuwen.core.single_agent.BaseAgent agent, Progress progress) {
                calls += 1;
            }
        }

        TrackingCallbacks tracking = new TrackingCallbacks();
        tracking.onTrainEpochBegin(null, progress);
        assertThat(tracking.calls).isEqualTo(1);
    }

    private static List<Integer> toList(Iterable<Integer> iterable) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        return values;
    }
}

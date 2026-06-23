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
 *
 * <p>Also mirrors Python's {@code tests.unit_tests.agent_evolving.trainer.test_progress} in
 * {@code tests/unit_tests/agent_evolving/trainer/test_progress.py}.</p>
 */
class ProgressTest {

    @Test
    void defaultInitUsesFactoryDefaults() {
        Progress progress = makeProgress();

        assertThat(progress.getStartEpoch()).isZero();
        assertThat(progress.getCurrentEpoch()).isZero();
        assertThat(progress.getMaxEpoch()).isEqualTo(3);
        assertThat(progress.getBestScore()).isZero();
    }

    @Test
    void customInitPreservesOverrides() {
        Progress progress = makeProgress(0, 0, 10, 0, 1, 0.85d, 0.0d, 0.0d);

        assertThat(progress.getMaxEpoch()).isEqualTo(10);
        assertThat(progress.getBestScore()).isEqualTo(0.85d);
    }

    @Test
    void runEpochYieldsEpochsAndUpdatesCurrentEpoch() {
        Progress progress = makeProgress();

        assertThat(toList(progress.runEpoch())).containsExactly(1, 2, 3);
        assertThat(progress.getCurrentEpoch()).isEqualTo(3);
    }

    @Test
    void runEpochRespectsStartEpoch() {
        Progress progress = makeProgress(2, 0, 5, 0, 1, 0.0d, 0.0d, 0.0d);

        assertThat(toList(progress.runEpoch())).containsExactly(3, 4, 5);
        assertThat(progress.getCurrentEpoch()).isEqualTo(5);
    }

    @Test
    void runEpochNoIterationsKeepsCurrentAtMaxEpoch() {
        Progress progress = makeProgress(5, 0, 5, 0, 1, 0.0d, 0.0d, 0.0d);

        assertThat(toList(progress.runEpoch())).isEmpty();
        assertThat(progress.getCurrentEpoch()).isEqualTo(5);
    }

    @Test
    void runBatchYieldsZeroBasedIterations() {
        Progress progress = makeProgress(0, 0, 3, 0, 3, 0.0d, 0.0d, 0.0d);

        assertThat(toList(progress.runBatch())).containsExactly(0, 1, 2);
        assertThat(progress.getCurrentBatchIter()).isEqualTo(2);
    }

    @Test
    void runBatchResetsBestBatchScore() {
        Progress progress = makeProgress(0, 0, 3, 0, 2, 0.0d, 0.9d, 0.0d);

        toList(progress.runBatch());

        assertThat(progress.getBestBatchScore()).isZero();
    }

    @Test
    void runBatchSingleIteration() {
        Progress progress = makeProgress();

        assertThat(toList(progress.runBatch())).containsExactly(0);
    }

    @Test
    void runBatchAllowsNoIterations() {
        Progress progress = makeProgress(0, 0, 3, 0, 0, 0.0d, 0.0d, 0.0d);

        assertThat(toList(progress.runBatch())).isEmpty();
    }

    @Test
    void scoreRangeAcceptsInRangeScore() {
        Progress progress = makeProgress(0, 0, 3, 0, 1, 0.5d, 0.0d, 0.0d);

        assertThat(progress.getBestScore()).isEqualTo(0.5d);
    }

    @Test
    void epochRangeAcceptsNonNegativeEpochs() {
        Progress progress = makeProgress(1, 0, 10, 0, 1, 0.0d, 0.0d, 0.0d);

        assertThat(progress.getStartEpoch()).isEqualTo(1);
    }

    @Test
    void pydanticFieldBoundsAreEnforced() {
        Progress progress = makeProgress();

        assertThatThrownBy(() -> progress.setMaxEpoch(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> progress.setBestScore(1.1d)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> progress.setCurrentEpochScore(1.0d)).doesNotThrowAnyException();
    }

    @Test
    void callbacksInitIsNoop() {
        assertThatCode(Callbacks::new).doesNotThrowAnyException();
    }

    @Test
    void onTrainBeginIsNoop() {
        Callbacks callbacks = new Callbacks();
        Progress progress = makeProgress();

        assertThatCode(() -> callbacks.onTrainBegin(null, progress, List.of())).doesNotThrowAnyException();
    }

    @Test
    void onTrainEndIsNoop() {
        Callbacks callbacks = new Callbacks();
        Progress progress = makeProgress();

        assertThatCode(() -> callbacks.onTrainEnd(null, progress, List.of())).doesNotThrowAnyException();
    }

    @Test
    void onTrainEpochBeginIsNoop() {
        Callbacks callbacks = new Callbacks();
        Progress progress = makeProgress();

        assertThatCode(() -> callbacks.onTrainEpochBegin(null, progress)).doesNotThrowAnyException();
    }

    @Test
    void onTrainEpochEndIsNoop() {
        Callbacks callbacks = new Callbacks();
        Progress progress = makeProgress();

        assertThatCode(() -> callbacks.onTrainEpochEnd(null, progress, List.of())).doesNotThrowAnyException();
    }

    @Test
    void callbacksCanBeSubclassedAndOverridden() {
        List<String> calls = new ArrayList<>();

        class TrackingCallbacks extends Callbacks {
            @Override
            public void onTrainBegin(com.openjiuwen.core.single_agent.BaseAgent agent, Progress progress,
                    List<com.openjiuwen.agent_evolving.dataset.EvaluatedCase> evalInfo) {
                calls.add("begin");
            }

            @Override
            public void onTrainEpochBegin(com.openjiuwen.core.single_agent.BaseAgent agent, Progress progress) {
                calls.add("epoch_begin");
            }
        }

        TrackingCallbacks tracking = new TrackingCallbacks();
        tracking.onTrainBegin(null, makeProgress(), List.of());
        tracking.onTrainEpochBegin(null, makeProgress());

        assertThat(calls).containsExactly("begin", "epoch_begin");
    }

    private static Progress makeProgress() {
        return makeProgress(0, 0, 3, 0, 1, 0.0d, 0.0d, 0.0d);
    }

    private static Progress makeProgress(int startEpoch, int currentEpoch, int maxEpoch, int currentBatchIter,
            int maxBatchIter, double bestScore, double bestBatchScore, double currentEpochScore) {
        Progress progress = new Progress();
        progress.setStartEpoch(startEpoch);
        progress.setCurrentEpoch(currentEpoch);
        progress.setMaxEpoch(maxEpoch);
        progress.setCurrentBatchIter(currentBatchIter);
        progress.setMaxBatchIter(maxBatchIter);
        progress.setBestScore(bestScore);
        progress.setBestBatchScore(bestBatchScore);
        progress.setCurrentEpochScore(currentEpochScore);
        return progress;
    }

    private static List<Integer> toList(Iterable<Integer> iterable) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        return values;
    }
}

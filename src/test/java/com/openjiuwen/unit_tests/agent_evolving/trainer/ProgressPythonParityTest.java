/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.trainer.Callbacks;
import com.openjiuwen.agent_evolving.trainer.Progress;
import com.openjiuwen.core.singleagent.BaseAgent;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for training progress and callback hooks.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.trainer.test_progress} in
 * {@code tests/unit_tests/agent_evolving/trainer/test_progress.py}.</p>
 */
class ProgressPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_evolving/trainer/test_progress.py";

    @TestFactory
    Collection<DynamicTest> pythonProgressCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::TestProgress::test_default_init",
                SOURCE + "::TestProgress::test_custom_init",
                SOURCE + "::TestProgress::test_run_epoch_yields_epochs",
                SOURCE + "::TestProgress::test_run_epoch_respects_start_epoch",
                SOURCE + "::TestProgress::test_run_epoch_no_iterations",
                SOURCE + "::TestProgress::test_run_batch_yields_iterations",
                SOURCE + "::TestProgress::test_run_batch_resets_best_score",
                SOURCE + "::TestProgress::test_run_batch_single_iteration",
                SOURCE + "::TestProgress::test_run_batch_no_iterations",
                SOURCE + "::TestProgress::test_score_range",
                SOURCE + "::TestProgress::test_epoch_range",
                SOURCE + "::TestCallbacks::test_init_is_noop",
                SOURCE + "::TestCallbacks::test_on_train_begin_noop",
                SOURCE + "::TestCallbacks::test_on_train_end_noop",
                SOURCE + "::TestCallbacks::test_on_train_epoch_begin_noop",
                SOURCE + "::TestCallbacks::test_on_train_epoch_end_noop",
                SOURCE + "::TestCallbacks::test_subclass_override"
        );
    }

    private static void runPythonCase(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::TestProgress::test_default_init" -> testDefaultInit();
            case SOURCE + "::TestProgress::test_custom_init" -> testCustomInit();
            case SOURCE + "::TestProgress::test_run_epoch_yields_epochs" -> testRunEpochYieldsEpochs();
            case SOURCE + "::TestProgress::test_run_epoch_respects_start_epoch" -> testRunEpochRespectsStartEpoch();
            case SOURCE + "::TestProgress::test_run_epoch_no_iterations" -> testRunEpochNoIterations();
            case SOURCE + "::TestProgress::test_run_batch_yields_iterations" -> testRunBatchYieldsIterations();
            case SOURCE + "::TestProgress::test_run_batch_resets_best_score" -> testRunBatchResetsBestScore();
            case SOURCE + "::TestProgress::test_run_batch_single_iteration" -> testRunBatchSingleIteration();
            case SOURCE + "::TestProgress::test_run_batch_no_iterations" -> testRunBatchNoIterations();
            case SOURCE + "::TestProgress::test_score_range" -> testScoreRange();
            case SOURCE + "::TestProgress::test_epoch_range" -> testEpochRange();
            case SOURCE + "::TestCallbacks::test_init_is_noop" -> testInitIsNoop();
            case SOURCE + "::TestCallbacks::test_on_train_begin_noop" -> testOnTrainBeginNoop();
            case SOURCE + "::TestCallbacks::test_on_train_end_noop" -> testOnTrainEndNoop();
            case SOURCE + "::TestCallbacks::test_on_train_epoch_begin_noop" -> testOnTrainEpochBeginNoop();
            case SOURCE + "::TestCallbacks::test_on_train_epoch_end_noop" -> testOnTrainEpochEndNoop();
            case SOURCE + "::TestCallbacks::test_subclass_override" -> testSubclassOverride();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testDefaultInit() {
        Progress progress = makeProgress();

        assertThat(progress.getStartEpoch()).isZero();
        assertThat(progress.getCurrentEpoch()).isZero();
        assertThat(progress.getMaxEpoch()).isEqualTo(3);
        assertThat(progress.getBestScore()).isZero();
    }

    private static void testCustomInit() {
        Progress progress = makeProgress(item -> {
            item.setMaxEpoch(10);
            item.setBestScore(0.85d);
        });

        assertThat(progress.getMaxEpoch()).isEqualTo(10);
        assertThat(progress.getBestScore()).isEqualTo(0.85d);
    }

    private static void testRunEpochYieldsEpochs() {
        Progress progress = makeProgress(item -> item.setMaxEpoch(3));

        assertThat(toList(progress.runEpoch())).containsExactly(1, 2, 3);
        assertThat(progress.getCurrentEpoch()).isEqualTo(3);
    }

    private static void testRunEpochRespectsStartEpoch() {
        Progress progress = makeProgress(item -> {
            item.setStartEpoch(2);
            item.setMaxEpoch(5);
        });

        assertThat(toList(progress.runEpoch())).containsExactly(3, 4, 5);
        assertThat(progress.getCurrentEpoch()).isEqualTo(5);
    }

    private static void testRunEpochNoIterations() {
        Progress progress = makeProgress(item -> {
            item.setStartEpoch(5);
            item.setMaxEpoch(5);
        });

        assertThat(toList(progress.runEpoch())).isEmpty();
        assertThat(progress.getCurrentEpoch()).isEqualTo(5);
    }

    private static void testRunBatchYieldsIterations() {
        Progress progress = makeProgress(item -> item.setMaxBatchIter(3));

        assertThat(toList(progress.runBatch())).containsExactly(0, 1, 2);
        assertThat(progress.getCurrentBatchIter()).isEqualTo(2);
    }

    private static void testRunBatchResetsBestScore() {
        Progress progress = makeProgress(item -> item.setMaxBatchIter(2));
        progress.setBestBatchScore(0.9d);

        toList(progress.runBatch());

        assertThat(progress.getBestBatchScore()).isZero();
    }

    private static void testRunBatchSingleIteration() {
        Progress progress = makeProgress(item -> item.setMaxBatchIter(1));

        assertThat(toList(progress.runBatch())).containsExactly(0);
    }

    private static void testRunBatchNoIterations() {
        Progress progress = makeProgress(item -> item.setMaxBatchIter(0));

        assertThat(toList(progress.runBatch())).isEmpty();
    }

    private static void testScoreRange() {
        Progress progress = makeProgress(item -> item.setBestScore(0.5d));

        assertThat(progress.getBestScore()).isEqualTo(0.5d);
    }

    private static void testEpochRange() {
        Progress progress = makeProgress(item -> {
            item.setStartEpoch(1);
            item.setMaxEpoch(10);
        });

        assertThat(progress.getStartEpoch()).isEqualTo(1);
    }

    private static void testInitIsNoop() {
        assertThatCode(Callbacks::new).doesNotThrowAnyException();
    }

    private static void testOnTrainBeginNoop() {
        Callbacks callbacks = makeCallbacks();

        assertThatCode(() -> callbacks.onTrainBegin(null, makeProgress(), List.of())).doesNotThrowAnyException();
    }

    private static void testOnTrainEndNoop() {
        Callbacks callbacks = makeCallbacks();

        assertThatCode(() -> callbacks.onTrainEnd(null, makeProgress(), List.of())).doesNotThrowAnyException();
    }

    private static void testOnTrainEpochBeginNoop() {
        Callbacks callbacks = makeCallbacks();

        assertThatCode(() -> callbacks.onTrainEpochBegin(null, makeProgress())).doesNotThrowAnyException();
    }

    private static void testOnTrainEpochEndNoop() {
        Callbacks callbacks = makeCallbacks();

        assertThatCode(() -> callbacks.onTrainEpochEnd(null, makeProgress(), List.of())).doesNotThrowAnyException();
    }

    private static void testSubclassOverride() {
        List<String> calls = new ArrayList<>();
        class CustomCallbacks extends Callbacks {
            @Override
            public void onTrainBegin(
                    BaseAgent agent,
                    Progress progress,
                    List<com.openjiuwen.agent_evolving.dataset.EvaluatedCase> evalInfo
            ) {
                calls.add("begin");
            }

            @Override
            public void onTrainEpochBegin(BaseAgent agent, Progress progress) {
                calls.add("epoch_begin");
            }
        }

        CustomCallbacks callbacks = new CustomCallbacks();
        callbacks.onTrainBegin(null, makeProgress(), List.of());
        callbacks.onTrainEpochBegin(null, makeProgress());

        assertThat(calls).containsExactly("begin", "epoch_begin");
    }

    private static Progress makeProgress() {
        return makeProgress(item -> {
        });
    }

    private static Progress makeProgress(Consumer<Progress> customizer) {
        Progress progress = new Progress();
        progress.setStartEpoch(0);
        progress.setMaxEpoch(3);
        progress.setBestScore(0.0d);
        progress.setCurrentEpoch(0);
        progress.setCurrentBatchIter(0);
        progress.setMaxBatchIter(1);
        progress.setBestBatchScore(0.0d);
        progress.setCurrentEpochScore(0.0d);
        customizer.accept(progress);
        return progress;
    }

    private static Callbacks makeCallbacks() {
        return new Callbacks();
    }

    private static List<Integer> toList(Iterable<Integer> iterable) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        return values;
    }
}

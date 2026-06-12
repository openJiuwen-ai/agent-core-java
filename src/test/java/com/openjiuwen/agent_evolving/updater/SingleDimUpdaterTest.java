/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.operator.Operator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the single-dimension updater.
 *
 * <p>Mirrors Python's {@code SingleDimUpdater} in
 * {@code openjiuwen/agent_evolving/updater/single_dim.py}.</p>
 */
class SingleDimUpdaterTest {

    @Test
    void bindDelegatesToOptimizerWithExplicitTargets() {
        RecordingOptimizer optimizer = new RecordingOptimizer(3, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        Map<String, Object> config = Map.of("limit", 1);

        int result = updater.bind(Map.of("op1", new RecordingOperator("op1")), List.of("target1"), config);

        assertEquals(3, result);
        assertEquals(List.of("target1"), optimizer.boundTargets);
        assertSame(config, optimizer.boundConfig);
        assertEquals(1, optimizer.boundOperators.size());
    }

    @Test
    void bindWithNullTargetsUsesConfigTargets() {
        RecordingOptimizer optimizer = new RecordingOptimizer(2, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        updater.bind(Map.of("op1", new RecordingOperator("op1")), null, Map.of("targets", List.of("prompt")));

        assertEquals(List.of("prompt"), optimizer.boundTargets);
    }

    @Test
    void bindWithEmptyTargetsUsesConfigTargets() {
        RecordingOptimizer optimizer = new RecordingOptimizer(2, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        updater.bind(Map.of("op1", new RecordingOperator("op1")), List.of(), Map.of("targets", List.of("memory")));

        assertEquals(List.of("memory"), optimizer.boundTargets);
    }

    @Test
    void requiresForwardDataDelegatesToOptimizer() {
        RecordingOptimizer optimizer = new RecordingOptimizer(0, new Updates());
        optimizer.requiresForwardData = false;
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        assertFalse(updater.requiresForwardData());

        optimizer.requiresForwardData = true;

        assertTrue(updater.requiresForwardData());
    }

    @Test
    void processAddsTrajectoriesRunsBackwardThenStep() throws Exception {
        Updates expectedUpdates = Updates.of("op1", "target", "new_value");
        RecordingOptimizer optimizer = new RecordingOptimizer(0, expectedUpdates);
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        List<Trajectory> trajectories = List.of(new Trajectory(), new Trajectory());
        List<EvolutionSignal> signals = List.of(new EvolutionSignal("low_score", "Troubleshooting", "score=0.00", null, Map.of()));

        Object result = updater.process(trajectories, signals, Map.of()).toCompletableFuture().get();

        assertSame(expectedUpdates, result);
        assertEquals(trajectories, optimizer.trajectories);
        assertSame(signals, optimizer.backwardSignals);
        assertEquals(1, optimizer.backwardCalls);
        assertEquals(1, optimizer.stepCalls);
    }

    @Test
    void processWithEmptyTrajectoriesStillRunsBackwardAndStep() throws Exception {
        RecordingOptimizer optimizer = new RecordingOptimizer(0, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        updater.process(List.of(), List.of(), Map.of()).toCompletableFuture().get();

        assertEquals(List.of(), optimizer.trajectories);
        assertEquals(1, optimizer.backwardCalls);
        assertEquals(1, optimizer.stepCalls);
    }

    @Test
    void processPreservesTrajectoryOrder() throws Exception {
        RecordingOptimizer optimizer = new RecordingOptimizer(0, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        Trajectory first = new Trajectory();
        Trajectory second = new Trajectory();

        updater.process(List.of(first, second), List.of(), Map.of()).toCompletableFuture().get();

        assertSame(first, optimizer.trajectories.get(0));
        assertSame(second, optimizer.trajectories.get(1));
    }

    @Test
    void updateReturnsUpdatesFromOptimizerStep() throws Exception {
        Updates expectedUpdates = Updates.of("op1", "prompt", "new prompt");
        RecordingOptimizer optimizer = new RecordingOptimizer(0, expectedUpdates);
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        Object result = updater.update(List.of(), List.of(), Map.of()).toCompletableFuture().get();

        assertSame(expectedUpdates, result);
    }

    @Test
    void updateAdaptsEvaluatedCasesToSignals() throws Exception {
        RecordingOptimizer optimizer = new RecordingOptimizer(0, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        EvaluatedCase evaluatedCase = evaluatedCase(0.0d, "reason");

        updater.update(List.of(), List.of(evaluatedCase), Map.of()).toCompletableFuture().get();

        assertEquals(1, optimizer.backwardSignals.size());
        assertEquals("low_score", optimizer.backwardSignals.get(0).getSignalType());
    }

    @Test
    void updateRespectsScoreThresholdFromConfig() throws Exception {
        RecordingOptimizer optimizer = new RecordingOptimizer(0, new Updates());
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("score_threshold", 1.0d);

        updater.update(List.of(), List.of(evaluatedCase(1.0d, "good"), evaluatedCase(0.0d, "bad")), config)
                .toCompletableFuture()
                .get();

        assertEquals(1, optimizer.backwardSignals.size());
        assertEquals("low_score", optimizer.backwardSignals.get(0).getSignalType());
    }

    @Test
    void getStateReturnsEmptyMap() {
        SingleDimUpdater updater = new SingleDimUpdater(new RecordingOptimizer(0, new Updates()));

        assertEquals(Map.of(), updater.getState());
    }

    @Test
    void loadStateIsNoop() {
        SingleDimUpdater updater = new SingleDimUpdater(new RecordingOptimizer(0, new Updates()));

        updater.loadState(Map.of("key", "value"));

        assertEquals(Map.of(), updater.getState());
    }

    private static EvaluatedCase evaluatedCase(double score, String reason) {
        return new EvaluatedCase(
                new Case(Map.of("query", "q"), Map.of("answer", "a")),
                Map.of("output", "pred"),
                score,
                reason,
                null
        );
    }

    private static final class RecordingOptimizer extends BaseOptimizer {
        private final int bindReturn;
        private final Updates stepReturn;

        private boolean requiresForwardData = true;
        private Map<String, Operator> boundOperators = Map.of();
        private List<String> boundTargets;
        private Map<String, Object> boundConfig = Map.of();
        private final List<Trajectory> trajectories = new ArrayList<>();
        private List<EvolutionSignal> backwardSignals = List.of();
        private int backwardCalls;
        private int stepCalls;

        private RecordingOptimizer(int bindReturn, Updates stepReturn) {
            this.bindReturn = bindReturn;
            this.stepReturn = stepReturn;
        }

        @Override
        public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
            this.boundOperators = operators;
            this.boundTargets = targets;
            this.boundConfig = config;
            return bindReturn;
        }

        @Override
        public boolean requiresForwardData() {
            return requiresForwardData;
        }

        @Override
        public void addTrajectory(Trajectory trajectory) {
            trajectories.add(trajectory);
        }

        @Override
        public CompletionStage<Void> backward(List<EvolutionSignal> signals) {
            backwardCalls++;
            backwardSignals = signals;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Updates step() {
            stepCalls++;
            return stepReturn;
        }

        @Override
        protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected Updates doStep() {
            return stepReturn;
        }
    }

    private static final class RecordingOperator extends Operator {
        private final String operatorId;

        private RecordingOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, com.openjiuwen.core.operator.TunableSpec> getTunables() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void setParameter(String target, Object value) {
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }
}

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
 * Tests for multi-dimensional updater defaults.
 *
 * <p>Mirrors Python's {@code MultiDimUpdater} in
 * {@code openjiuwen/agent_evolving/updater/multi_dim.py}.</p>
 */
class MultiDimUpdaterTest {

    @Test
    void requiresForwardDataReturnsTrueWhenAnyDomainOptimizerRequiresIt() {
        RecordingMultiDimUpdater updater = new RecordingMultiDimUpdater(Map.of(
                "llm", new FlagOptimizer(false),
                "tool", new FlagOptimizer(true)
        ));

        assertTrue(updater.requiresForwardData());
    }

    @Test
    void requiresForwardDataReturnsFalseForNoRequiringOptimizers() {
        RecordingMultiDimUpdater updater = new RecordingMultiDimUpdater(Map.of(
                "llm", new FlagOptimizer(false)
        ));

        assertFalse(updater.requiresForwardData());
        assertEquals(1, updater.getDomainOptimizers().size());
    }

    @Test
    void processAcceptsSignalsDirectly() throws Exception {
        RecordingMultiDimUpdater updater = new RecordingMultiDimUpdater(Map.of());
        EvolutionSignal signal = new EvolutionSignal("low_score", "Troubleshooting", "score=0.00", null, Map.of());

        Object result = updater.process(List.of(), List.of(signal), Map.of()).toCompletableFuture().get();

        assertSame(RecordingMultiDimUpdater.RESULT, result);
        assertEquals(List.of(signal), updater.signals);
    }

    @Test
    void updateConvertsEvaluatedCasesToSignalsAndDelegatesToProcess() throws Exception {
        RecordingMultiDimUpdater updater = new RecordingMultiDimUpdater(Map.of());
        List<Trajectory> trajectories = List.of(new Trajectory());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("score_threshold", 0.6d);
        List<Object> evaluatedCases = List.of(
                evaluatedCase(0.8d, "good"),
                evaluatedCase(0.4d, "bad")
        );

        Object result = updater.update(trajectories, evaluatedCases, config).toCompletableFuture().get();

        assertSame(RecordingMultiDimUpdater.RESULT, result);
        assertSame(trajectories, updater.trajectories);
        assertSame(config, updater.config);
        assertEquals(1, updater.signals.size());
        assertEquals("bad", updater.signals.get(0).getContext().get("reason"));
    }

    @Test
    void updateConvertsMultipleEvaluatedCasesToSignalsInOrder() throws Exception {
        RecordingMultiDimUpdater updater = new RecordingMultiDimUpdater(Map.of());
        List<Object> evaluatedCases = List.of(
                evaluatedCase(1.0d, "perfect"),
                evaluatedCase(0.0d, "reason")
        );

        Object result = updater.update(List.of(), evaluatedCases, Map.of()).toCompletableFuture().get();

        assertSame(RecordingMultiDimUpdater.RESULT, result);
        assertEquals(2, updater.signals.size());
        assertEquals(1.0d, updater.signals.get(0).getContext().get("score"));
        assertEquals(0.0d, updater.signals.get(1).getContext().get("score"));
        assertEquals("evaluated", updater.signals.get(0).getSignalType());
        assertEquals("low_score", updater.signals.get(1).getSignalType());
    }

    private static EvaluatedCase evaluatedCase(double score, String reason) {
        return new EvaluatedCase(
                new Case(Map.of("question", "q"), Map.of("answer", "a")),
                Map.of("text", "model answer"),
                score,
                reason,
                null
        );
    }

    private static final class FlagOptimizer extends BaseOptimizer {
        private final boolean requiresForwardData;

        private FlagOptimizer(boolean requiresForwardData) {
            this.requiresForwardData = requiresForwardData;
        }

        @Override
        public boolean requiresForwardData() {
            return requiresForwardData;
        }

        @Override
        protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected Updates doStep() {
            return new Updates();
        }
    }

    private static final class RecordingMultiDimUpdater extends MultiDimUpdater {
        private static final Object RESULT = new Object();

        private List<Trajectory> trajectories = List.of();
        private List<EvolutionSignal> signals = List.of();
        private Map<String, Object> config = Map.of();

        private RecordingMultiDimUpdater(Map<String, ? extends BaseOptimizer> domainOptimizers) {
            super(domainOptimizers);
        }

        @Override
        public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
            return 0;
        }

        @Override
        public CompletionStage<Object> process(
                List<Trajectory> trajectories,
                List<EvolutionSignal> signals,
                Map<String, Object> config
        ) {
            this.trajectories = trajectories;
            this.signals = new ArrayList<>(signals);
            this.config = config;
            return CompletableFuture.completedFuture(RESULT);
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }
}

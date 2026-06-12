/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

class BaseOptimizerTest {

    @Test
    void filterOperatorsMatchesMapBasedTunables() {
        Map<String, Operator> operators = Map.of(
                "op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system"))),
                "op2", new FakeOperator("op2", Map.of("other", new TunableSpec("other", "prompt", "other")))
        );

        Map<String, Operator> result = BaseOptimizer.filterOperators(operators, List.of("system_prompt"));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("op1"));
    }

    @Test
    void bindInitializesParametersForMatchingOperators() {
        TestOptimizer optimizer = new TestOptimizer();

        int count = optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void bindUsesDefaultTargetsWhenTargetsEmpty() {
        TestOptimizer optimizer = new TestOptimizer();

        int count = optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of(),
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void addTrajectoryCachesCopyAccessibleList() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        Trajectory trajectory = Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build();
        optimizer.addTrajectory(trajectory);

        List<Trajectory> trajectories = optimizer.getTrajectories();
        assertEquals(1, trajectories.size());
        assertNotSame(trajectories, optimizer.getTrajectories());
    }

    @Test
    void backwardAndStepDelegateToSubclassHooks() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        EvolutionSignal onlineSignal = EvolutionSignal.builder().signalType("user_intent").skillName("demo").build();
        EvolutionSignal offlineSignal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .skillName("demo")
                .context(Map.of("score", 0))
                .build();

        optimizer.backward(List.of(onlineSignal, offlineSignal)).toCompletableFuture().join();
        Updates updates = optimizer.step();

        assertEquals(1, optimizer.backwardCallCount);
        assertEquals(1, optimizer.stepCallCount);
        assertEquals("value", updates.get("op1", "system_prompt"));
        assertEquals(2, optimizer.capturedSignals.size());
        assertEquals(2, optimizer.selectedSignals.size());
        assertTrue(optimizer.getTrajectories().isEmpty());
    }

    @Test
    void stepWithoutBoundParametersRaisesToolchainError() {
        TestOptimizer optimizer = new TestOptimizer();

        assertThrows(BaseError.class, optimizer::step);
    }

    @Test
    void textualParameterSupportsDynamicGradientValues() {
        TextualParameter parameter = new TextualParameter("op1");

        parameter.setGradient("system_prompt", "text gradient");
        parameter.setGradient("examples", List.of("a", "b"));
        parameter.setDescription("demo");

        assertEquals("text gradient", parameter.getGradient("system_prompt"));
        assertEquals(List.of("a", "b"), parameter.getGradient("examples"));
        assertEquals("demo", parameter.getDescription());
    }

    @Test
    void defaultSelectSignalsKeepsAllSignals() {
        TestOptimizer optimizer = new TestOptimizer();

        List<EvolutionSignal> selected = optimizer.selectSignals(List.of(
                EvolutionSignal.builder().signalType("a").build(),
                EvolutionSignal.builder().signalType("b").build()
        ));

        assertEquals(2, selected.size());
    }

    @Test
    void contextManagerCompatibilityMethodsAreNoOp() {
        TestOptimizer optimizer = new TestOptimizer();

        assertEquals(optimizer, optimizer.enter());
        assertEquals(optimizer, optimizer.aenter().toCompletableFuture().join());
        assertEquals(null, optimizer.aexit(null, null, null).toCompletableFuture().join());
        optimizer.close();
        assertFalse(false);
    }

    private static final class TestOptimizer extends BaseOptimizer {

        private int backwardCallCount;
        private int stepCallCount;
        private List<EvolutionSignal> capturedSignals = List.of();

        @Override
        protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
            backwardCallCount++;
            capturedSignals = List.copyOf(signals);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected Updates doStep() {
            stepCallCount++;
            return Updates.of("op1", "system_prompt", "value");
        }

        @Override
        public List<String> defaultTargets() {
            return List.of("system_prompt");
        }
    }

    private static final class FakeOperator extends Operator {

        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeOperator(String operatorId, Map<String, TunableSpec> tunables) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>(tunables);
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return tunables;
        }

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>(state);
        }

        @Override
        public void setParameter(String target, Object value) {
            state.put(target, value);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.state.clear();
            if (state != null) {
                this.state.putAll(state);
            }
        }
    }
}

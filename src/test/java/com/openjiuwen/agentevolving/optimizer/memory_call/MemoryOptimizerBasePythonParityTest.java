/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.memory_call;

import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for memory optimizer base behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.memory_call.test_memory_base} in
 * {@code tests/unit_tests/agent_evolving/optimizer/memory_call/test_memory_base.py}.</p>
 */
class MemoryOptimizerBasePythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/agent_evolving/optimizer/memory_call/test_memory_base.py";
    private static final String ENABLED = "enabled";
    private static final String MAX_RETRIES = "max_retries";

    @TestFactory
    Collection<DynamicTest> pythonMemoryOptimizerBaseCases() {
        return List.of(
                caseOf("TestMemoryOptimizerBaseInit::test_domain_is_memory",
                        MemoryOptimizerBasePythonParityTest::domainIsMemory),
                caseOf("TestMemoryOptimizerBaseInit::test_default_targets_enabled_and_max_retries",
                        MemoryOptimizerBasePythonParityTest::defaultTargetsEnabledAndMaxRetries),
                caseOf("TestMemoryOptimizerBaseFilterOperators::test_filter_matches_memory_targets",
                        MemoryOptimizerBasePythonParityTest::filterMatchesMemoryTargets),
                caseOf("TestMemoryOptimizerBaseFilterOperators::test_filter_empty_targets",
                        MemoryOptimizerBasePythonParityTest::filterEmptyTargets),
                caseOf("TestMemoryOptimizerBaseFilterOperators::test_filter_skips_no_tunables",
                        MemoryOptimizerBasePythonParityTest::filterSkipsNoTunables),
                caseOf("TestMemoryOptimizerBaseFilterOperators::test_filter_with_partial_targets",
                        MemoryOptimizerBasePythonParityTest::filterWithPartialTargets),
                caseOf("TestMemoryOptimizerBaseBind::test_bind_with_memory_operators",
                        MemoryOptimizerBasePythonParityTest::bindWithMemoryOperators),
                caseOf("TestMemoryOptimizerBaseBind::test_bind_with_no_matching_operators",
                        MemoryOptimizerBasePythonParityTest::bindWithNoMatchingOperators),
                caseOf("TestMemoryOptimizerBaseBind::test_bind_with_multiple_matching",
                        MemoryOptimizerBasePythonParityTest::bindWithMultipleMatching),
                caseOf("TestMemoryOptimizerBaseDefaultTargets::test_default_targets_returns_list",
                        MemoryOptimizerBasePythonParityTest::defaultTargetsReturnsList),
                caseOf("TestMemoryOptimizerBaseDefaultTargets::test_default_targets_contains_enabled",
                        MemoryOptimizerBasePythonParityTest::defaultTargetsContainsEnabled),
                caseOf("TestMemoryOptimizerBaseDefaultTargets::test_default_targets_contains_max_retries",
                        MemoryOptimizerBasePythonParityTest::defaultTargetsContainsMaxRetries),
                caseOf("TestMemoryOptimizerBaseDefaultTargets::test_default_targets_count",
                        MemoryOptimizerBasePythonParityTest::defaultTargetsCount)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void domainIsMemory() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertThat(optimizer.domainValue()).isEqualTo("memory");
    }

    private static void defaultTargetsEnabledAndMaxRetries() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();
        List<String> targets = optimizer.defaultTargets();

        assertThat(targets).contains(ENABLED, MAX_RETRIES);
    }

    private static void filterMatchesMemoryTargets() {
        Map<String, Operator> operators = Map.of(
                "op1", memoryOperator("memory_op", Map.of(ENABLED, true, MAX_RETRIES, 3)),
                "op2", memoryOperator("memory_op", Map.of("system_prompt", "prompt"))
        );

        Map<String, Operator> result = MemoryOptimizerBase.filterOperators(operators, List.of(ENABLED, MAX_RETRIES));

        assertThat(result).containsKey("op1");
        assertThat(result).doesNotContainKey("op2");
    }

    private static void filterEmptyTargets() {
        Map<String, Operator> operators = Map.of("op1", memoryOperator());

        Map<String, Operator> result = MemoryOptimizerBase.filterOperators(operators, List.of());

        assertThat(result).isEmpty();
    }

    private static void filterSkipsNoTunables() {
        Map<String, Operator> operators = Map.of("op1", memoryOperator("memory_op", Map.of()));

        Map<String, Operator> result = MemoryOptimizerBase.filterOperators(operators, List.of(ENABLED));

        assertThat(result).isEmpty();
    }

    private static void filterWithPartialTargets() {
        Map<String, Operator> operators = Map.of(
                "op1", memoryOperator("op1", Map.of(ENABLED, true)),
                "op2", memoryOperator("op2", Map.of(MAX_RETRIES, 3))
        );

        Map<String, Operator> result = MemoryOptimizerBase.filterOperators(operators, List.of(ENABLED, MAX_RETRIES));

        assertThat(result).containsKeys("op1", "op2");
    }

    private static void bindWithMemoryOperators() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();
        Map<String, Operator> operators = Map.of(
                "op1", memoryOperator("memory_op", Map.of(ENABLED, true, MAX_RETRIES, 3)),
                "op2", memoryOperator("memory_op", Map.of("system_prompt", "prompt"))
        );

        int count = optimizer.bind(operators, null, Map.of());

        assertThat(count).isEqualTo(1);
    }

    private static void bindWithNoMatchingOperators() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();
        Map<String, Operator> operators = Map.of("op1", memoryOperator("memory_op", Map.of("other", "value")));

        int count = optimizer.bind(operators, null, Map.of());

        assertThat(count).isZero();
    }

    private static void bindWithMultipleMatching() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();
        Map<String, Operator> operators = Map.of(
                "op1", memoryOperator("op1", Map.of(ENABLED, true)),
                "op2", memoryOperator("op2", Map.of(MAX_RETRIES, 3))
        );

        int count = optimizer.bind(operators, null, Map.of());

        assertThat(count).isEqualTo(2);
    }

    private static void defaultTargetsReturnsList() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertThat(optimizer.defaultTargets()).isInstanceOf(List.class);
    }

    private static void defaultTargetsContainsEnabled() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertThat(optimizer.defaultTargets()).contains(ENABLED);
    }

    private static void defaultTargetsContainsMaxRetries() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertThat(optimizer.defaultTargets()).contains(MAX_RETRIES);
    }

    private static void defaultTargetsCount() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertThat(optimizer.defaultTargets()).hasSize(2);
    }

    private static FakeMemoryOperator memoryOperator() {
        return memoryOperator("memory_op", Map.of(ENABLED, true, MAX_RETRIES, 3));
    }

    private static FakeMemoryOperator memoryOperator(String operatorId, Map<String, Object> tunables) {
        return new FakeMemoryOperator(operatorId, tunables, Map.of(ENABLED, true, MAX_RETRIES, 3));
    }

    private static final class TestMemoryOptimizerBase extends MemoryOptimizerBase {

        private String domainValue() {
            return domain;
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

    private static final class FakeMemoryOperator extends Operator {
        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private final Map<String, Object> state;

        private FakeMemoryOperator(String operatorId, Map<String, Object> tunables, Map<String, Object> state) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>();
            tunables.forEach((key, value) -> this.tunables.put(key, new TunableSpec(key, "memory", key)));
            this.state = new LinkedHashMap<>(state);
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return new LinkedHashMap<>(tunables);
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

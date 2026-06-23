/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental parity tests for the LLM-call optimizer base.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.llm_call.test_llm_base} in
 * {@code tests/unit_tests/agent_evolving/optimizer/llm_call/test_llm_base.py}.</p>
 */
class LLMCallOptimizerBaseMissingTest {

    @Test
    void domainIsLlm() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        assertThat(optimizer.exposeDomain()).isEqualTo("llm");
    }

    @Test
    void defaultTargetsContainSystemAndUserPrompt() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        assertThat(optimizer.defaultTargets())
                .contains(LLMCallOptimizerBase.SYSTEM_PROMPT)
                .contains(LLMCallOptimizerBase.USER_PROMPT);
    }

    @Test
    void filterMatchesPromptTargets() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", mockOperator("op1", Map.of(LLMCallOptimizerBase.SYSTEM_PROMPT, "prompt")),
                "op2", mockOperator("op2", Map.of(LLMCallOptimizerBase.USER_PROMPT, "prompt")),
                "op3", mockOperator("op3", Map.of("other", "value"))
        );

        Map<String, Operator> result = optimizer.exposeFilterOperators(
                operators,
                List.of(LLMCallOptimizerBase.SYSTEM_PROMPT, LLMCallOptimizerBase.USER_PROMPT)
        );

        assertThat(result).containsKeys("op1", "op2");
        assertThat(result).doesNotContainKey("op3");
    }

    @Test
    void filterEmptyTargetsReturnsEmptyMap() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        Map<String, Operator> result = optimizer.exposeFilterOperators(
                Map.of("op1", mockOperator("op1", Map.of(LLMCallOptimizerBase.SYSTEM_PROMPT, "prompt"))),
                List.of()
        );

        assertThat(result).isEmpty();
    }

    @Test
    void filterSkipsNoTunables() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        Map<String, Operator> result = optimizer.exposeFilterOperators(
                Map.of("op1", mockOperator("op1", Map.of())),
                List.of(LLMCallOptimizerBase.SYSTEM_PROMPT)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void bindWithLlmOperators() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();
        Map<String, Operator> operators = Map.of(
                "op1", mockOperator("op1", Map.of(LLMCallOptimizerBase.SYSTEM_PROMPT, "prompt")),
                "op2", mockOperator("op2", Map.of("tool_description", "desc"))
        );

        int count = optimizer.bind(operators, List.of(), Map.of());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void bindWithNoMatchingOperatorsReturnsZero() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        int count = optimizer.bind(
                Map.of("op1", mockOperator("op1", Map.of("other", "value"))),
                List.of(),
                Map.of()
        );

        assertThat(count).isZero();
    }

    private static FakeOperator mockOperator(String operatorId, Map<String, String> tunableNames) {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        tunableNames.forEach((name, kind) -> tunables.put(name, new TunableSpec(name, kind, name)));
        return new FakeOperator(operatorId, tunables, Map.of(LLMCallOptimizerBase.SYSTEM_PROMPT, "You are helpful."));
    }

    /**
     * Mirrors Python's concrete `LLMCallOptimizerBase` test instance in
     * {@code tests/unit_tests/agent_evolving/optimizer/llm_call/test_llm_base.py}.
     */
    private static final class TestLLMCallOptimizer extends LLMCallOptimizerBase {

        private String exposeDomain() {
            return domain;
        }

        private Map<String, Operator> exposeFilterOperators(Map<String, Operator> operators, List<String> targets) {
            return LLMCallOptimizerBase.filterOperators(operators, targets);
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

    /**
     * Mirrors Python's `make_mock_llm_operator` helper in
     * {@code tests/unit_tests/agent_evolving/optimizer/llm_call/test_llm_base.py}.
     */
    private static final class FakeOperator extends Operator {
        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private final Map<String, Object> state;

        private FakeOperator(String operatorId, Map<String, TunableSpec> tunables, Map<String, Object> state) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>(tunables);
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

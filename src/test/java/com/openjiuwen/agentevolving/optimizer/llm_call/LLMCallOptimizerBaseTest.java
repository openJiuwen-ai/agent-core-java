/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LLM-call optimizer base behavior.
 *
 * <p>Mirrors Python's {@code LLMCallOptimizerBase} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/base.py}.</p>
 */
class LLMCallOptimizerBaseTest {

    @Test
    void defaultTargetsMatchPythonOrder() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();

        assertEquals(List.of("system_prompt", "user_prompt"), optimizer.defaultTargets());
    }

    @Test
    void filterOperatorsDelegatesToBaseOptimizer() {
        Map<String, Operator> operators = Map.of(
                "op1", new FakeOperator(
                        "op1",
                        Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")),
                        Map.of()
                ),
                "op2", new FakeOperator(
                        "op2",
                        Map.of("temperature", new TunableSpec("temperature", "number", "temperature")),
                        Map.of()
                )
        );

        Map<String, Operator> filtered = LLMCallOptimizerBase.filterOperators(
                operators,
                List.of("system_prompt", "user_prompt")
        );

        assertEquals(1, filtered.size());
        assertTrue(filtered.containsKey("op1"));
    }

    @Test
    void targetFrozenUsesOperatorTunables() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();
        Operator operator = new FakeOperator(
                "op1",
                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")),
                Map.of()
        );

        assertFalse(optimizer.exposeIsTargetFrozen(operator, "system_prompt"));
        assertTrue(optimizer.exposeIsTargetFrozen(operator, "user_prompt"));
    }

    @Test
    void promptTemplateUsesStateValueOrEmptyString() {
        TestLLMCallOptimizer optimizer = new TestLLMCallOptimizer();
        Operator operator = new FakeOperator(
                "op1",
                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")),
                Map.of("system_prompt", "You are precise.")
        );

        PromptTemplate systemTemplate = optimizer.exposeGetPromptTemplate(operator, "system_prompt");
        PromptTemplate missingTemplate = optimizer.exposeGetPromptTemplate(operator, "user_prompt");

        assertEquals("You are precise.", systemTemplate.getContent());
        assertEquals("", missingTemplate.getContent());
    }

    private static final class TestLLMCallOptimizer extends LLMCallOptimizerBase {

        private boolean exposeIsTargetFrozen(Operator operator, String target) {
            return isTargetFrozen(operator, target);
        }

        private PromptTemplate exposeGetPromptTemplate(Operator operator, String target) {
            return getPromptTemplate(operator, target);
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

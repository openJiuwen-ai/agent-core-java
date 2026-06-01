package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the LLM-call optimizer base.
 *
 * <p>Mirrors Python's {@code test_llm_base.py} in
 * {@code tests/unit_tests/agent_evolving/optimizer/llm_call}.
 */
class LLMCallOptimizerBaseTest {

    @Test
    void domainAndDefaultTargetsMirrorPythonBase() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();

        assertEquals("llm", optimizer.getDomain());
        assertEquals(List.of("system_prompt", "user_prompt"), optimizer.defaultTargets());
    }

    @Test
    void filterOperatorsMatchesPromptTargets() {
        Map<String, Object> operators = Map.of(
                "op1", operator(Map.of("system_prompt", tunable("system_prompt")), Map.of()),
                "op2", operator(Map.of("user_prompt", tunable("user_prompt")), Map.of()),
                "op3", operator(Map.of("other", tunable("other")), Map.of())
        );

        Map<String, Object> result = LLMCallOptimizerBase.filterOperators(
                operators,
                List.of("system_prompt", "user_prompt")
        );

        assertTrue(result.containsKey("op1"));
        assertTrue(result.containsKey("op2"));
        assertFalse(result.containsKey("op3"));
    }

    @Test
    void filterEmptyTargetsReturnsEmptyMap() {
        Map<String, Object> result = LLMCallOptimizerBase.filterOperators(
                Map.of("op1", operator(Map.of("system_prompt", tunable("system_prompt")), Map.of())),
                List.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void filterSkipsOperatorsWithoutTunables() {
        Map<String, Object> result = LLMCallOptimizerBase.filterOperators(
                Map.of("op1", operator(Map.of(), Map.of())),
                List.of("system_prompt")
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void bindUsesDefaultPromptTargetsWhenTargetsAreNull() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();

        int count = optimizer.bind(
                Map.of("op1", operator(Map.of("system_prompt", tunable("system_prompt")), Map.of())),
                null,
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void bindWithNoMatchingOperatorsReturnsZero() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();

        int count = optimizer.bind(
                Map.of("op1", operator(Map.of("other", tunable("other")), Map.of())),
                null,
                Map.of()
        );

        assertEquals(0, count);
        assertTrue(optimizer.parameters().isEmpty());
    }

    @Test
    void targetFrozenDependsOnOperatorTunables() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();
        Operator op = operator(Map.of("system_prompt", tunable("system_prompt")), Map.of());

        assertFalse(optimizer.isFrozen(op, "system_prompt"));
        assertTrue(optimizer.isFrozen(op, "user_prompt"));
    }

    @Test
    void getPromptTemplateReadsStateAndDefaultsMissingTargetToEmpty() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();
        Operator op = operator(
                Map.of("system_prompt", tunable("system_prompt")),
                Map.of("system_prompt", "You are helpful.")
        );

        PromptTemplate systemTemplate = optimizer.promptTemplate(op, "system_prompt");
        PromptTemplate missingTemplate = optimizer.promptTemplate(op, "user_prompt");

        assertEquals("You are helpful.", systemTemplate.getContent());
        assertEquals("", missingTemplate.getContent());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyBaseLlmCallOptimizerRetainsTypeAndCallContract() {
        BaseLlmCallOptimizer optimizer = new BaseLlmCallOptimizer("legacy") {
            @Override
            public Object optimizeLlmCall(Object callContext) {
                return Map.of("context", callContext);
            }
        };

        assertEquals("legacy", optimizer.getOptimizerType());
        assertEquals(Map.of("context", "input"), optimizer.optimizeLlmCall("input"));
    }

    private static TunableSpec tunable(String name) {
        return new TunableSpec(name, "prompt", name);
    }

    private static Operator operator(Map<String, TunableSpec> tunables, Map<String, Object> state) {
        Operator operator = mock(Operator.class);
        when(operator.getOperatorId()).thenReturn("llm_op");
        when(operator.getTunables()).thenReturn(new LinkedHashMap<>(tunables));
        when(operator.getState()).thenReturn(new LinkedHashMap<>(state));
        return operator;
    }

    private static final class TestLlmOptimizerBase extends LLMCallOptimizerBase {

        private boolean isFrozen(Object op, String target) {
            return isTargetFrozen(op, target);
        }

        private PromptTemplate promptTemplate(Object op, String target) {
            return getPromptTemplate(op, target);
        }

        @Override
        protected Updates doStep() {
            return new Updates();
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }

}

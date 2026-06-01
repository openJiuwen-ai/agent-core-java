package com.openjiuwen.agent_evolving.optimizer.memory_call;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the memory optimizer base.
 *
 * <p>Mirrors Python's {@code test_memory_base.py} in
 * {@code tests/unit_tests/agent_evolving/optimizer/memory_call}.
 */
class MemoryOptimizerBaseTest {

    @Test
    void testDomainIsMemory() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertEquals("memory", optimizer.getDomain());
    }

    @Test
    void testDefaultTargetsEnabledAndMaxRetries() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertIterableEquals(List.of("enabled", "max_retries"), optimizer.defaultTargets());
    }

    @Test
    void testFilterMatchesMemoryTargets() {
        Map<String, Object> result = MemoryOptimizerBase.filterOperators(Map.of(
                "op1", operator("op1", Map.of("enabled", tunable("enabled"), "max_retries", tunable("max_retries"))),
                "op2", operator("op2", Map.of("system_prompt", tunable("system_prompt")))
        ), List.of("enabled", "max_retries"));

        assertTrue(result.containsKey("op1"));
        assertTrue(!result.containsKey("op2"));
    }

    @Test
    void testFilterEmptyTargets() {
        Map<String, Object> result = MemoryOptimizerBase.filterOperators(
                Map.of("op1", operator("op1", Map.of("enabled", tunable("enabled")))),
                List.of()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterSkipsNoTunables() {
        Map<String, Object> result = MemoryOptimizerBase.filterOperators(
                Map.of("op1", operator("op1", Map.of())),
                List.of("enabled")
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterWithPartialTargets() {
        Map<String, Object> result = MemoryOptimizerBase.filterOperators(Map.of(
                "op1", operator("op1", Map.of("enabled", tunable("enabled"))),
                "op2", operator("op2", Map.of("max_retries", tunable("max_retries")))
        ), List.of("enabled", "max_retries"));

        assertTrue(result.containsKey("op1"));
        assertTrue(result.containsKey("op2"));
    }

    @Test
    void testBindWithMemoryOperators() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        int count = optimizer.bind(Map.of(
                "op1", operator("op1", Map.of("enabled", tunable("enabled"))),
                "op2", operator("op2", Map.of("system_prompt", tunable("system_prompt")))
        ), null, Map.of());

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("op1"));
    }

    @Test
    void testBindWithNoMatchingOperators() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        int count = optimizer.bind(
                Map.of("op1", operator("op1", Map.of("other", tunable("other")))),
                null,
                Map.of()
        );

        assertEquals(0, count);
    }

    @Test
    void testBindWithMultipleMatching() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        int count = optimizer.bind(Map.of(
                "op1", operator("op1", Map.of("enabled", tunable("enabled"))),
                "op2", operator("op2", Map.of("max_retries", tunable("max_retries")))
        ), null, Map.of());

        assertEquals(2, count);
    }

    @Test
    void testDefaultTargetsReturnsList() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertTrue(List.class.isInstance(optimizer.defaultTargets()));
    }

    @Test
    void testDefaultTargetsContainsEnabled() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertTrue(optimizer.defaultTargets().contains("enabled"));
    }

    @Test
    void testDefaultTargetsContainsMaxRetries() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertTrue(optimizer.defaultTargets().contains("max_retries"));
    }

    @Test
    void testDefaultTargetsCount() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertEquals(2, optimizer.defaultTargets().size());
    }

    @Test
    void usesMemoryDomainAndDefaultTargets() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        assertEquals("memory", optimizer.getDomain());
        assertIterableEquals(List.of("enabled", "max_retries"), optimizer.defaultTargets());
    }

    @Test
    void bindUsesMemoryTargetsByDefault() {
        TestMemoryOptimizerBase optimizer = new TestMemoryOptimizerBase();

        int count = optimizer.bind(Map.of(
                "memory", operator("memory", Map.of(
                        "enabled", new TunableSpec("enabled", "bool", "enabled"),
                        "max_retries", new TunableSpec("max_retries", "int", "max_retries")
                )),
                "llm", operator("llm", Map.of(
                        "system_prompt", new TunableSpec("system_prompt", "prompt", "sys")
                ))
        ), null, Map.of());

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("memory"));
    }

    private static Operator operator(String operatorId, Map<String, TunableSpec> tunables) {
        Operator operator = mock(Operator.class);
        when(operator.getOperatorId()).thenReturn(operatorId);
        when(operator.getTunables()).thenReturn(tunables);
        return operator;
    }

    private static TunableSpec tunable(String name) {
        return new TunableSpec(name, "value", name);
    }

    private static final class TestMemoryOptimizerBase extends MemoryOptimizerBase {

        @Override
        protected Updates doStep() {
            return new Updates();
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }
}

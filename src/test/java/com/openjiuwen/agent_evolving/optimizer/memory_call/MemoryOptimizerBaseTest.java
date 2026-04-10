/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
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

class MemoryOptimizerBaseTest {

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

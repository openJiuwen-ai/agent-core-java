/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolOptimizerBaseTest {

    @Test
    void inheritedDomainAndDefaultTargetsMatchPython() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        assertEquals("tool", optimizer.getDomain());
        assertEquals(List.of("tool_description"), optimizer.defaultTargets());
    }

    @Test
    void bindUsesDefaultToolDescriptionTarget() {
        TestToolOptimizer optimizer = new TestToolOptimizer();

        int count = optimizer.bind(
                Map.of(
                        "tool", new FakeOperator(Map.of("tool_description", "desc")),
                        "other", new FakeOperator(Map.of("system_prompt", "prompt"))
                ),
                null,
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.getOperators().containsKey("tool"));
    }

    private static final class TestToolOptimizer extends ToolOptimizerBase {

        @Override
        protected Updates doStep() {
            return new Updates();
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }

    public static final class FakeOperator {
        private final Map<String, Object> tunables;

        private FakeOperator(Map<String, Object> tunables) {
            this.tunables = new LinkedHashMap<>(tunables);
        }

        public Map<String, Object> getTunables() {
            return tunables;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactMatchMetricTest {

    @Test
    void normalizeIgnoresCaseAndWhitespace() {
        ExactMatchMetric metric = new ExactMatchMetric(true);

        assertEquals(1.0, metric.compute(" Hello\tWorld ", "hello world", Map.of()));
    }

    @Test
    void nonNormalizedModeUsesPythonStringRules() {
        ExactMatchMetric metric = new ExactMatchMetric(false);

        assertEquals(1.0, metric.compute(true, "True", Map.of()));
        assertEquals(0.0, metric.compute("Hello", "hello", Map.of()));
    }

    @Test
    void baseMetricContractStillHolds() {
        ExactMatchMetric metric = new ExactMatchMetric();

        assertEquals("exact_match", metric.getName());
        assertTrue(metric.isHigherIsBetter());
        assertIterableEquals(
                List.of(1.0, 0.0),
                metric.computeBatch(List.of("a", "b"), List.of("a", "a"), Map.of())
        );
    }
}

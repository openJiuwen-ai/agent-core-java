/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestMetricBase} in
 * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics/test_base.py}.
 */
class MetricTest {

    @Test
    void namePropertyMatchesPythonContract() {
        assertEquals("test_metric", new ConcreteMetric().getName());
    }

    @Test
    void higherIsBetterDefaultsTrue() {
        assertTrue(new ConcreteMetric().isHigherIsBetter());
    }

    @Test
    void computeBatchReturnsScores() {
        List<Object> results = new ConcreteMetric().computeBatch(List.of("a", "b", "a"), List.of("a", "a", "a"), Map.of());
        assertEquals(List.of(1.0d, 0.0d, 1.0d), results);
    }

    @Test
    void computeBatchEmptyReturnsEmptyList() {
        assertTrue(new ConcreteMetric().computeBatch(List.of(), List.of(), Map.of()).isEmpty());
    }

    /**
     * Mirrors Python's {@code ConcreteMetric} in
     * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics/test_base.py}.
     */
    private static final class ConcreteMetric extends Metric {
        @Override
        public String getName() {
            return "test_metric";
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            return prediction != null && prediction.equals(label) ? 1.0d : 0.0d;
        }
    }
}

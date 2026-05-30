/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for base metric functionality.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.evaluator.test_metrics.test_base}.
 */
class BaseMetricTest {

    @Test
    void testMetricNameIsSet() {
        TestMetric metric = new TestMetric("test_metric");
        assertEquals("test_metric", metric.getName());
    }

    @Test
    void testHigherIsBetterDefaultsTrue() {
        TestMetric metric = new TestMetric("test_metric");
        assertTrue(metric.isHigherIsBetter());
    }

    @Test
    void testMetricComputeReturnsScore() {
        TestMetric metric = new TestMetric("accuracy");
        Object result = metric.compute("expected answer", "expected answer", new HashMap<>());

        assertTrue(result instanceof Double);
        assertEquals(1.0, (Double) result);
    }

    @Test
    void testComputeBatchReturnsScores() {
        TestMetric metric = new TestMetric("batch_metric");

        List<Object> results = metric.computeBatch(List.of("a", "b", "a"), List.of("a", "a", "a"));

        assertIterableEquals(List.of(1.0, 0.0, 1.0), results);
    }

    @Test
    void testComputeBatchEmpty() {
        TestMetric metric = new TestMetric("batch_metric");

        assertTrue(metric.computeBatch(List.of(), List.of()).isEmpty());
    }

    @Test
    void testComputeBatchStopsAtShorterInput() {
        TestMetric metric = new TestMetric("batch_metric");

        List<Object> results = metric.computeBatch(List.of("a", "b", "c"), List.of("a"));

        assertIterableEquals(List.of(1.0), results);
    }

    @Test
    void testMetricComputeWithKwargs() {
        TestMetric metric = new TestMetric("contextual_metric");
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("question", "Test question");
        kwargs.put("case", Map.of("inputs", Map.of("q", "test")));

        Object result = metric.compute("prediction", "label", kwargs);

        assertNotNull(result);
    }

    @Test
    void testMetricComputeHandlesNullPrediction() {
        TestMetric metric = new TestMetric("null_safe_metric");

        Object result = metric.compute(null, "expected", new HashMap<>());

        assertEquals(0.0, (Double) result);
    }

    @Test
    void testMetricComputeHandlesNullLabel() {
        TestMetric metric = new TestMetric("null_safe_metric");

        Object result = metric.compute("prediction", null, new HashMap<>());

        assertEquals(0.0, (Double) result);
    }

    @Test
    void testMetricReturnsMapForMultiDimensional() {
        MultiDimMetric metric = new MultiDimMetric();

        Object result = metric.compute("pred", "label", new HashMap<>());

        assertTrue(result instanceof Map);
        Map<?, ?> scores = (Map<?, ?>) result;
        assertTrue(scores.containsKey("accuracy"));
        assertTrue(scores.containsKey("relevance"));
    }

    @Test
    void testMetricScoreInRange() {
        TestMetric metric = new TestMetric("bounded_metric");
        
        for (int i = 0; i < 10; i++) {
            Object result = metric.compute("pred_" + i, "label_" + i, new HashMap<>());
            Double score = (Double) result;
            assertTrue(score >= 0.0 && score <= 1.0);
        }
    }

    // Test helper classes

    private static class TestMetric extends Metric {
        private final String name;

        TestMetric(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            if (prediction == null || label == null) {
                return 0.0;
            }
            return prediction.equals(label) ? 1.0 : 0.0;
        }
    }

    private static class MultiDimMetric extends Metric {
        @Override
        public String getName() {
            return "multi_dim";
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            Map<String, Object> scores = new HashMap<>();
            scores.put("accuracy", 0.8);
            scores.put("relevance", 0.6);
            return scores;
        }
    }
}

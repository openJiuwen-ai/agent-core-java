package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactMatchMetricTest {

    @Test
    void computeSupportsRawAndNormalizedComparisons() {
        ExactMatchMetric raw = new ExactMatchMetric(false);
        ExactMatchMetric normalized = new ExactMatchMetric(true);

        assertEquals(1.0, raw.compute("hello", "hello"));
        assertEquals(0.0, raw.compute("Hello", "hello"));
        assertEquals(1.0, normalized.compute(" Hello\tWorld ", "hello world"));
        assertEquals(0.0, normalized.compute(null, ""));
        assertEquals(1.0, normalized.compute("True", true));
        assertEquals(1.0, normalized.compute("1.5", 1.5));
        assertEquals(1.0, normalized.compute("", ""));
        assertEquals(0.0, normalized.compute("hello!", "hello."));
    }

    @Test
    void exactMatchMetricExposesNameAndHigherIsBetter() {
        ExactMatchMetric metric = new ExactMatchMetric();

        assertEquals("exact_match", metric.getName());
        assertTrue(metric.isHigherIsBetter());
    }

    @Test
    void metricBaseContractMatchesPythonDefaults() {
        ContractMetric metric = new ContractMetric();

        assertEquals("test_metric", metric.getName());
        assertTrue(metric.isHigherIsBetter());
        assertIterableEquals(
                List.of(1.0, 0.0, 1.0),
                metric.computeBatch(List.of("a", "b", "a"), List.of("a", "a", "a"))
        );
        assertIterableEquals(List.of(), metric.computeBatch(List.of(), List.of()));
        assertIterableEquals(
                List.of("seen"),
                metric.computeBatch(List.of("a"), List.of("a"), Map.of("tag", "seen"))
        );
    }

    private static final class ContractMetric extends Metric {
        @Override
        public String getName() {
            return "test_metric";
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            Object tag = kwargs != null ? kwargs.get("tag") : null;
            if (tag != null) {
                return tag;
            }
            return prediction != null && prediction.equals(label) ? 1.0 : 0.0;
        }
    }
}

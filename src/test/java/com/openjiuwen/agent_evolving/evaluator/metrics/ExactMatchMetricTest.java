package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @Test
    void exactMatchMetricExposesNameAndHigherIsBetter() {
        ExactMatchMetric metric = new ExactMatchMetric();

        assertEquals("exact_match", metric.getName());
        assertTrue(metric.isHigherIsBetter());
    }
}

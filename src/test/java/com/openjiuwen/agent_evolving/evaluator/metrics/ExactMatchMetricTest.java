package com.openjiuwen.agent_evolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ExactMatchMetric.
 *
 * <p>Mirrors Python's {@code test_exact_match.py} in
 * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics}.
 */
class ExactMatchMetricTest {

    @Test
    void identicalStringsMatch() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertEquals(1.0, metric.compute("hello", "hello"));
    }

    @Test
    void differentStringsNoMatch() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertEquals(0.0, metric.compute("hello", "world"));
    }

    @Test
    void caseSensitiveByDefaultWhenNormalizeFalse() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertEquals(0.0, metric.compute("Hello", "hello"));
    }

    @Test
    void normalizeIgnoresCase() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("Hello", "hello"));
    }

    @Test
    void normalizeIgnoresWhitespace() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("hello world", "hello   world"));
    }

    @Test
    void normalizeIgnoresLeadingTrailing() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute(" hello ", "hello"));
    }

    @Test
    void normalizeConvertsTabsNewlines() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("hello\nworld", "hello world"));
        assertEquals(1.0, metric.compute("hello\tworld", "hello world"));
    }

    @Test
    void normalizeCollapsesSpaces() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("hello     world", "hello world"));
    }

    @Test
    void normalizeHandlesNone() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(0.0, metric.compute(null, ""));
        assertEquals(0.0, metric.compute("x", null));
    }

    @Test
    void normalizeWithNumericValues() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute(123, 123));
        assertEquals(1.0, metric.compute("123", 123));
    }

    @Test
    void normalizeWithMixedTypes() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("True", true));
        assertEquals(1.0, metric.compute("False", false));
    }

    @Test
    void normalizeWithFloatStrings() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("1.5", 1.5));
    }

    @Test
    void nameProperty() {
        assertEquals("exact_match", new ExactMatchMetric().getName());
    }

    @Test
    void higherIsBetterProperty() {
        assertTrue(new ExactMatchMetric().isHigherIsBetter());
    }

    @Test
    void computeAcceptsKwargs() {
        ExactMatchMetric metric = new ExactMatchMetric();

        assertEquals(1.0, metric.compute("a", "a", Map.of("extra_param", "ignored")));
    }

    @Test
    void normalizeEmptyStrings() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("", ""));
    }

    @Test
    void normalizeHandlesSpecialChars() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(0.0, metric.compute("hello!", "hello."));
    }

    @Test
    void normalizeHandlesUnicode() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertEquals(1.0, metric.compute("caf\u00e9", "Caf\u00e9"));
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

    private static ExactMatchMetric makeExactMatchMetric(boolean normalize) {
        return new ExactMatchMetric(normalize);
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

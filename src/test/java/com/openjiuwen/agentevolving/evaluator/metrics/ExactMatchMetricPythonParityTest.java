/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestExactMatchMetric} in
 * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics/test_exact_match.py}.
 */
class ExactMatchMetricPythonParityTest {

    @Test
    void testIdenticalStringsMatch() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertThat(score(metric, "hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void testDifferentStringsNoMatch() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertThat(score(metric, "hello", "world")).isEqualTo(0.0);
    }

    @Test
    void testCaseSensitiveByDefault() {
        ExactMatchMetric metric = makeExactMatchMetric(false);

        assertThat(score(metric, "Hello", "hello")).isEqualTo(0.0);
    }

    @Test
    void testNormalizeIgnoresCase() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "Hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeIgnoresWhitespace() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "hello world", "hello   world")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeIgnoresLeadingTrailing() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, " hello ", "hello")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeConvertsTabsNewlines() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "hello\nworld", "hello world")).isEqualTo(1.0);
        assertThat(score(metric, "hello\tworld", "hello world")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeCollapsesSpaces() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "hello     world", "hello world")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeHandlesNone() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, null, "")).isEqualTo(0.0);
        assertThat(score(metric, "x", null)).isEqualTo(0.0);
    }

    @Test
    void testNormalizeWithNumericValues() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, 123, 123)).isEqualTo(1.0);
        assertThat(score(metric, "123", 123)).isEqualTo(1.0);
    }

    @Test
    void testNormalizeWithMixedTypes() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "True", true)).isEqualTo(1.0);
        assertThat(score(metric, "False", false)).isEqualTo(1.0);
    }

    @Test
    void testNormalizeWithFloatStrings() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "1.5", 1.5)).isEqualTo(1.0);
    }

    @Test
    void testNameProperty() {
        assertThat(new ExactMatchMetric().getName()).isEqualTo("exact_match");
    }

    @Test
    void testHigherIsBetterProperty() {
        assertThat(new ExactMatchMetric().isHigherIsBetter()).isTrue();
    }

    @Test
    void testComputeAcceptsKwargs() {
        ExactMatchMetric metric = new ExactMatchMetric();

        assertThat(metric.compute("a", "a", Map.of("extra_param", "ignored"))).isEqualTo(1.0);
    }

    @Test
    void testNormalizeEmptyStrings() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "", "")).isEqualTo(1.0);
    }

    @Test
    void testNormalizeHandlesSpecialChars() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "hello!", "hello.")).isEqualTo(0.0);
    }

    @Test
    void testNormalizeHandlesUnicode() {
        ExactMatchMetric metric = makeExactMatchMetric(true);

        assertThat(score(metric, "caf\u00e9", "Caf\u00e9")).isEqualTo(1.0);
    }

    private static ExactMatchMetric makeExactMatchMetric(boolean normalize) {
        return new ExactMatchMetric(normalize);
    }

    private static Object score(ExactMatchMetric metric, Object prediction, Object label) {
        return metric.compute(prediction, label, Map.of());
    }
}

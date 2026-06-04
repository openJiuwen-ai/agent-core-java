package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.metrics.Metric;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MetricEvaluator and aggregate functions.
 *
 * <p>Mirrors Python's {@code test_evaluator.py} in
 * {@code tests/unit_tests/agent_evolving/evaluator}.
 */
class MetricEvaluatorTest {

    @Test
    void aggScoreMeanSingleValue() {
        assertEquals(0.5, MetricEvaluator.aggScore(List.of(0.5), "mean"), 1e-9);
    }

    @Test
    void aggScoreMeanCalculation() {
        assertEquals(0.5, MetricEvaluator.aggScore(List.of(0.5, 1.0, 0.0), "mean"), 1e-9);
    }

    @Test
    void aggScoreMeanEmptyList() {
        assertEquals(0.0, MetricEvaluator.aggScore(List.of(), "mean"), 1e-9);
    }

    @Test
    void aggScoreFirstReturnsFirst() {
        assertEquals(0.2, MetricEvaluator.aggScore(List.of(0.2, 0.8, 0.5), "first"), 1e-9);
    }

    @Test
    void aggScoreFirstEmptyList() {
        assertEquals(0.0, MetricEvaluator.aggScore(List.of(), "first"), 1e-9);
    }

    @Test
    void aggScoreDefaultIsMean() {
        assertEquals(2.0, MetricEvaluator.aggScore(List.of(1.0, 2.0, 3.0), null), 1e-9);
    }

    @Test
    void aggScoreInvalidAggregateDefaultsToMean() {
        assertEquals(2.0, MetricEvaluator.aggScore(List.of(1.0, 2.0, 3.0), "invalid"), 1e-9);
    }

    @Test
    void singleMetric() {
        FixedMetric metric = new FixedMetric("test_metric", 0.8);
        MetricEvaluator evaluator = new MetricEvaluator(metric);

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.8, result.getScore(), 1e-9);
        assertEquals(Map.of("test_metric", 0.8), result.getPerMetric());
        assertEquals(1, metric.computeCount);
    }

    @Test
    void multipleMetrics() {
        MetricEvaluator evaluator = new MetricEvaluator(
                List.of(new FixedMetric("metric1", 0.6), new FixedMetric("metric2", 0.8)),
                "mean"
        );

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.7, result.getScore(), 1e-9);
        assertEquals(Map.of("metric1", 0.6, "metric2", 0.8), result.getPerMetric());
    }

    @Test
    void metricReturnsDict() {
        MapMetric metric = new MapMetric("multi_metric", orderedScores("score_a", 1.0, "score_b", 0.5));
        MetricEvaluator evaluator = new MetricEvaluator(metric);

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.75, result.getScore(), 1e-9);
        assertEquals(Map.of("score_a", 1.0, "score_b", 0.5), result.getPerMetric());
    }

    @Test
    void emptyMetrics() {
        MetricEvaluator evaluator = new MetricEvaluator(List.of(), "mean");

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.0, result.getScore(), 1e-9);
        assertNull(result.getPerMetric());
    }

    @Test
    void aggregateFirst() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 0.9));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.9, result.getScore(), 1e-9);
    }

    @Test
    void convertsNumericStrings() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", "0.75"));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.75, result.getScore(), 1e-9);
    }

    @Test
    void convertsLiteralFloat() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 0.75));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.75, result.getScore(), 1e-9);
    }

    @Test
    void metricKwargsPassed() {
        CapturingMetric metric = new CapturingMetric();
        Case caseData = makeCase();
        MetricEvaluator evaluator = new MetricEvaluator(metric);

        evaluator.evaluate(caseData, Map.of("output", "pred"));

        assertNotNull(metric.lastKwargs);
        assertTrue(metric.lastKwargs.containsKey("question"));
        assertTrue(metric.lastKwargs.containsKey("case"));
        assertSame(caseData, metric.lastKwargs.get("case"));
    }

    @Test
    void evaluateWithEmptyPrediction() {
        FixedMetric metric = new FixedMetric("test_metric", 1.0);
        MetricEvaluator evaluator = new MetricEvaluator(metric);

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(1.0, result.getScore(), 1e-9);
        assertEquals(1, metric.computeCount);
    }

    @Test
    void evaluateWithEmptyDictPrediction() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 0.5));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of());

        assertEquals(0.5, result.getScore(), 1e-9);
    }

    @Test
    void evaluateWithSpecialCharsInPrediction() {
        CapturingMetric metric = new CapturingMetric(1.0);
        MetricEvaluator evaluator = new MetricEvaluator(metric);

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "Hello ! ? test"));

        assertEquals(1.0, result.getScore(), 1e-9);
        assertTrue(metric.lastKwargs.containsKey("question"));
    }

    @Test
    void evaluateWithVeryLongPrediction() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 0.8));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "a".repeat(10_000)));

        assertEquals(0.8, result.getScore(), 1e-9);
    }

    @Test
    void evaluateMultipleMetricsOneFails() {
        MetricEvaluator evaluator = new MetricEvaluator(List.of(
                new FixedMetric("good", 0.9),
                new FixedMetric("bad", "invalid_string_not_a_number")
        ), "mean");

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertNotNull(result.getPerMetric());
        assertEquals(0.9, result.getPerMetric().get("good"), 1e-9);
        assertEquals(0.0, result.getPerMetric().get("bad"), 1e-9);
    }

    @Test
    void evaluateWithNestedCaseInputs() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 1.0));
        Case caseData = new Case(
                Map.of(
                        "query", "test",
                        "metadata", Map.of("source", "test", "nested", Map.of("deep", Map.of("value", true)))
                ),
                Map.of("answer", "expected")
        );

        EvaluatedCase result = evaluator.evaluate(caseData, Map.of("output", "pred"));

        assertEquals(1.0, result.getScore(), 1e-9);
    }

    @Test
    void evaluateWithNumericLabel() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("test_metric", 0.7));
        Case caseData = new Case(Map.of("q", "test"), Map.of("answer", 42));

        EvaluatedCase result = evaluator.evaluate(caseData, Map.of("output", "pred"));

        assertEquals(0.7, result.getScore(), 1e-9);
    }

    @Test
    void scoreBoundaryZero() {
        assertMetricScore("zero", 0.0, 0.0);
    }

    @Test
    void scoreBoundaryOne() {
        assertMetricScore("one", 1.0, 1.0);
    }

    @Test
    void scoreExactlyHalf() {
        assertMetricScore("half", 0.5, 0.5);
    }

    @Test
    void scoreFractionalBoundary() {
        assertMetricScore("fraction", 0.999999, 0.999999);
    }

    @Test
    void verySmallPositiveScore() {
        assertMetricScore("tiny", 0.000001, 0.000001);
    }

    @Test
    void scoreOverOneNotClamped() {
        assertMetricScore("large", 999.0, 999.0);
    }

    @Test
    void negativeScoreNotClamped() {
        assertMetricScore("negative", -5.0, -5.0);
    }

    @Test
    void zeroAsValidValue() {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric("zero_val", 0.0));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.0, result.getScore(), 1e-9);
        assertEquals(Map.of("zero_val", 0.0), result.getPerMetric());
    }

    private static void assertMetricScore(String name, Object value, double expected) {
        MetricEvaluator evaluator = new MetricEvaluator(new FixedMetric(name, value));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(expected, result.getScore(), 1e-9);
    }

    private static Case makeCase() {
        return new Case(Map.of("q", "test question"), Map.of("ans", "expected answer"), "test_case_id");
    }

    private static LinkedHashMap<String, Object> orderedScores(
            String firstKey,
            Object firstValue,
            String secondKey,
            Object secondValue
    ) {
        LinkedHashMap<String, Object> scores = new LinkedHashMap<>();
        scores.put(firstKey, firstValue);
        scores.put(secondKey, secondValue);
        return scores;
    }

    private static class FixedMetric extends Metric {
        private final String name;
        private final Object value;
        private int computeCount;

        private FixedMetric(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            computeCount++;
            return value;
        }
    }

    private static final class MapMetric extends FixedMetric {
        private final Map<String, Object> scores;

        private MapMetric(String name, Map<String, Object> scores) {
            super(name, 0.0);
            this.scores = scores;
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            return scores;
        }
    }

    private static final class CapturingMetric extends Metric {
        private final Object value;
        private Map<String, Object> lastKwargs;

        private CapturingMetric() {
            this("0.75");
        }

        private CapturingMetric(Object value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return "capturing";
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            lastKwargs = kwargs;
            return value;
        }
    }
}

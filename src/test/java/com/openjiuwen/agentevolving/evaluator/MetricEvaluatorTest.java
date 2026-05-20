package com.openjiuwen.agentevolving.evaluator;

import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.evaluator.metrics.Metric;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricEvaluatorTest {

    @Test
    void evaluateAggregatesSingleAndMultipleMetrics() {
        Metric metric1 = new FixedMetric("metric1", 0.6);
        Metric metric2 = new FixedMetric("metric2", 0.8);
        MetricEvaluator evaluator = new MetricEvaluator(List.of(metric1, metric2), "mean");

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.7, result.getScore(), 1e-9);
        assertEquals(Map.of("metric1", 0.6, "metric2", 0.8), result.getPerMetric());
    }

    @Test
    void evaluateSupportsMetricMapsAndFirstAggregation() {
        Metric metric = new Metric() {
            @Override
            public String getName() {
                return "multi";
            }

            @Override
            public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
                LinkedHashMap<String, Object> scores = new LinkedHashMap<>();
                scores.put("score_b", 0.5);
                scores.put("score_a", 1.0);
                return scores;
            }
        };
        MetricEvaluator evaluator = new MetricEvaluator(List.of(metric), "first");

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.5, result.getScore(), 1e-9);
        assertEquals(Map.of("score_a", 1.0, "score_b", 0.5), result.getPerMetric());
    }

    @Test
    void evaluateConvertsNumericStringsAndPassesQuestionAndCaseKwargs() {
        CapturingMetric metric = new CapturingMetric();
        MetricEvaluator evaluator = new MetricEvaluator(List.of(metric), "mean");
        Case caseData = makeCase();

        EvaluatedCase result = evaluator.evaluate(caseData, Map.of("output", "pred"));

        assertEquals(0.75, result.getScore(), 1e-9);
        assertSame(caseData, metric.lastKwargs.get("case"));
        assertEquals(caseData.getInputs(), metric.lastKwargs.get("question"));
    }

    @Test
    void evaluateReturnsZeroAndNullPerMetricWhenNoMetricsConfigured() {
        MetricEvaluator evaluator = new MetricEvaluator(List.of(), "mean");

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.0, result.getScore());
        assertNull(result.getPerMetric());
    }

    private static Case makeCase() {
        return new Case(Map.of("q", "test"), Map.of("ans", "expected"), "case_1");
    }

    private static final class FixedMetric extends Metric {
        private final String name;
        private final Object value;

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
            return value;
        }
    }

    private static final class CapturingMetric extends Metric {
        private Map<String, Object> lastKwargs;

        @Override
        public String getName() {
            return "capturing";
        }

        @Override
        public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
            lastKwargs = kwargs;
            return "0.75";
        }
    }
}

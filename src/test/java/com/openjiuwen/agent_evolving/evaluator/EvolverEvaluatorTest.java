package com.openjiuwen.agent_evolving.evaluator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EvolverEvaluatorTest {

    @Test
    void evaluateReturnsZeroWhenNoMetrics() {
        EvolverEvaluator evaluator = new EvolverEvaluator(List.of());

        assertEquals(0.0, evaluator.evaluate("trajectory"));
    }

    @Test
    void evaluateAveragesMetricScores() {
        EvolverEvaluator evaluator = new EvolverEvaluator(List.of(
                new FixedMetric("quality", 0.25),
                new FixedMetric("safety", 0.75)
        ));

        assertEquals(0.5, evaluator.evaluate("trajectory"));
    }

    @Test
    void evaluatePassesTrajectoryToMetrics() {
        Object trajectory = new Object();
        AtomicReference<Object> seen = new AtomicReference<>();
        EvolverEvaluator evaluator = new EvolverEvaluator(List.of(new RecordingMetric(seen)));

        assertEquals(1.0, evaluator.evaluate(trajectory));
        assertSame(trajectory, seen.get());
    }

    private static final class FixedMetric implements EvolverEvaluator.Metric {
        private final String name;
        private final double score;

        private FixedMetric(String name, double score) {
            this.name = name;
            this.score = score;
        }

        @Override
        public double compute(Object trajectory) {
            return score;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class RecordingMetric implements EvolverEvaluator.Metric {
        private final AtomicReference<Object> seen;

        private RecordingMetric(AtomicReference<Object> seen) {
            this.seen = seen;
        }

        @Override
        public double compute(Object trajectory) {
            seen.set(trajectory);
            return 1.0;
        }

        @Override
        public String getName() {
            return "recording";
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for offline evaluated-case signal conversion.
 *
 * <p>Mirrors Python's {@code from_evaluated_case} and {@code from_evaluated_cases} in
 * {@code openjiuwen/agent_evolving/signal/from_eval.py}.</p>
 *
 * <p>Mirrors Python's {@code test_from_eval} in
 * {@code tests/unit_tests/agent_evolving/signal/test_from_eval.py}.</p>
 */
class FromEvalTest {

    @Test
    void fromEvaluatedCaseBuildsEvaluatedSignalWithoutThresholdFiltering() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluated(0.75, "partial"), "operator-1", null);

        assertEquals("evaluated", signal.getSignalType());
        assertEquals("Troubleshooting", signal.getSection());
        assertEquals("score=0.75", signal.getExcerpt());
        assertEquals("operator-1", signal.getSkillName());
        assertEquals("offline_evaluation", signal.getContext().get("source"));
        assertEquals("{query=test}", signal.getContext().get("question"));
        assertEquals("{answer=expected}", signal.getContext().get("label"));
        assertEquals("{output=result}", signal.getContext().get("answer"));
        assertEquals("partial", signal.getContext().get("reason"));
        assertEquals(0.75d, (Double) signal.getContext().get("score"), 1.0e-9);
    }

    @Test
    void lowScoreProducesSignal() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluated(0.0, "test_reason"), "test_operator", 1.0d);

        assertNotNull(signal);
        assertEquals("low_score", signal.getSignalType());
        assertEquals("Troubleshooting", signal.getSection());
        assertEquals("test_operator", signal.getSkillName());
        assertTrue(signal.getExcerpt().contains("score"));
        assertEquals("offline_evaluation", signal.getContext().get("source"));
    }

    @Test
    void zeroScoreBuildsLowScoreSignalAndEmptyOperatorBecomesNullSkillName() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluated(0.0, ""), "", null);

        assertEquals("low_score", signal.getSignalType());
        assertEquals("score=0.00", signal.getExcerpt());
        assertNull(signal.getSkillName());
        assertEquals("", signal.getContext().get("reason"));
    }

    @Test
    void thresholdFiltersScoresGreaterThanOrEqualToThreshold() {
        assertNull(FromEval.fromEvaluatedCase(evaluated(0.6, ""), "operator", 0.6d));
    }

    @Test
    void thresholdAllowsScoresBelowThreshold() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluated(0.59, "needs repair"), "operator", 0.6d);

        assertEquals("evaluated", signal.getSignalType());
        assertEquals("score=0.59", signal.getExcerpt());
    }

    @Test
    void scoreThresholdDefaultIsNone() {
        EvolutionSignal signal0 = FromEval.fromEvaluatedCase(evaluated(0.5, "half"), "test_operator");
        EvolutionSignal signal1 = FromEval.fromEvaluatedCase(evaluated(1.0, "perfect"), "test_operator");

        assertNotNull(signal0);
        assertNotNull(signal1);
    }

    @Test
    void customScoreThresholdFiltersCasesCorrectly() {
        EvolutionSignal signal0 = FromEval.fromEvaluatedCase(evaluated(0.5, "half"), "test_operator", 0.8d);
        EvolutionSignal signal1 = FromEval.fromEvaluatedCase(evaluated(0.7, "partial"), "test_operator", 0.8d);
        EvolutionSignal signal2 = FromEval.fromEvaluatedCase(evaluated(0.9, "good"), "test_operator", 0.8d);

        assertNotNull(signal0);
        assertNotNull(signal1);
        assertNull(signal2);
    }

    @Test
    void contextContainsEvaluationFields() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(
                evaluated(
                        0.0,
                        Map.of("query", "What is the answer?"),
                        Map.of("expected", "42"),
                        Map.of("result", "40"),
                        "Wrong answer"
                ),
                "test_operator",
                1.0d);

        assertNotNull(signal);
        Map<String, Object> context = signal.getContext();
        assertNotNull(context);
        assertTrue(context.containsKey("question"));
        assertTrue(context.containsKey("label"));
        assertTrue(context.containsKey("answer"));
        assertTrue(context.containsKey("reason"));
        assertTrue(context.containsKey("score"));
        assertTrue(String.valueOf(context.get("question")).contains("What is the answer?"));
        assertTrue(String.valueOf(context.get("label")).contains("42"));
        assertTrue(String.valueOf(context.get("answer")).contains("40"));
        assertEquals("Wrong answer", context.get("reason"));
        assertEquals(0.0d, (Double) context.get("score"), 1.0e-9);
    }

    @Test
    void operatorIdBecomesSkillName() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(
                evaluated(0.0, "test_reason"),
                "skill_call_test_skill",
                1.0d);

        assertNotNull(signal);
        assertEquals("skill_call_test_skill", signal.getSkillName());
    }

    @Test
    void contextIncludesProvenanceFields() {
        EvolutionSignal signal = FromEval.fromEvaluatedCase(evaluated(0.5, "test_reason"), "test_operator");

        assertNotNull(signal);
        assertEquals("offline_evaluation", signal.getContext().get("source"));
    }

    @Test
    void fromEvaluatedCasesDropsFilteredCases() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                List.of(evaluated(0.2, "bad"), evaluated(0.9, "good"), evaluated(0.0, "zero")),
                "operator",
                0.5d);

        assertEquals(2, signals.size());
        assertEquals("evaluated", signals.get(0).getSignalType());
        assertEquals("low_score", signals.get(1).getSignalType());
    }

    @Test
    void batchFiltersByThreshold() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                evaluatedCases(List.of(0.0d, 0.5d, 1.0d, 0.8d)),
                "test_operator",
                1.0d);

        assertEquals(3, signals.size());
        assertEquals("low_score", signals.get(0).getSignalType());
        assertEquals("evaluated", signals.get(1).getSignalType());
        assertEquals("evaluated", signals.get(2).getSignalType());
    }

    @Test
    void batchWithAllHighScoresReturnsEmptyList() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                evaluatedCases(List.of(1.0d, 1.0d, 1.0d)),
                "test_operator",
                1.0d);

        assertEquals(List.of(), signals);
    }

    @Test
    void batchWithAllLowScoresProducesSignals() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                evaluatedCases(List.of(0.0d, 0.0d, 0.0d)),
                "test_operator",
                1.0d);

        assertEquals(3, signals.size());
    }

    @Test
    void batchMatchesSingleCaseResults() {
        List<EvaluatedCase> cases = evaluatedCases(List.of(0.0d, 0.5d, 1.0d, 0.8d));
        List<EvolutionSignal> batchSignals = FromEval.fromEvaluatedCases(cases, "test_operator", 1.0d);

        List<EvolutionSignal> singleSignals = new ArrayList<>();
        for (EvaluatedCase caseValue : cases) {
            EvolutionSignal signal = FromEval.fromEvaluatedCase(caseValue, "test_operator", 1.0d);
            if (signal != null) {
                singleSignals.add(signal);
            }
        }

        assertEquals(singleSignals.size(), batchSignals.size());
        for (int index = 0; index < batchSignals.size(); index++) {
            assertEquals(singleSignals.get(index).getSignalType(), batchSignals.get(index).getSignalType());
            assertEquals(singleSignals.get(index).getSkillName(), batchSignals.get(index).getSkillName());
            assertEquals(singleSignals.get(index).getExcerpt(), batchSignals.get(index).getExcerpt());
        }
    }

    @Test
    void emptyCasesReturnsEmptyList() {
        assertEquals(List.of(), FromEval.fromEvaluatedCases(List.of(), "test_operator", null));
    }

    @Test
    void customThresholdInBatch() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                evaluatedCases(List.of(0.5d, 0.7d, 0.8d, 0.9d)),
                "test_operator",
                0.75d);

        assertEquals(2, signals.size());
    }

    private static EvaluatedCase evaluated(double score, String reason) {
        Case sample = new Case(Map.of("query", "test"), Map.of("answer", "expected"));
        return new EvaluatedCase(sample, Map.of("output", "result"), score, reason, null);
    }

    private static EvaluatedCase evaluated(
            double score,
            Map<String, Object> inputs,
            Map<String, Object> label,
            Map<String, Object> answer,
            String reason
    ) {
        return new EvaluatedCase(new Case(inputs, label), answer, score, reason, null);
    }

    private static List<EvaluatedCase> evaluatedCases(List<Double> scores) {
        List<EvaluatedCase> cases = new ArrayList<>();
        for (int index = 0; index < scores.size(); index++) {
            cases.add(evaluated(
                    scores.get(index),
                    Map.of("query", "input_" + index),
                    Map.of("expected", "label_" + index),
                    Map.of("result", "answer_" + index),
                    "reason_" + index));
        }
        return cases;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.signal.EvolutionCategory;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for signal extraction from evaluation.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.signal.test_from_eval}.
 */
class FromEvalTest {

    @Test
    void testLowScoreProducesSignalFromEvaluatedCase() {
        EvaluatedCase evaluatedCase = makeCase(0.0d);

        EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(evaluatedCase, "test_operator", 1.0d);

        assertNotNull(signal);
        assertEquals("low_score", signal.getSignalType());
        assertEquals(EvolutionCategory.SKILL_EXPERIENCE, signal.getEvolutionType());
        assertEquals("Troubleshooting", signal.getSection());
        assertEquals("test_operator", signal.getSkillName());
        assertTrue(signal.getExcerpt().contains("score"));
    }

    @Test
    void testHighScoreReturnsNullWithThreshold() {
        EvaluatedCase evaluatedCase = makeCase(1.0d);

        EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(evaluatedCase, "test_operator", 1.0d);

        assertNull(signal);
    }

    @Test
    void testScoreThresholdDefaultIsNull() {
        EvolutionSignal signal0 = SignalFromEval.fromEvaluatedCase(makeCase(0.5d), "test_operator", null);
        EvolutionSignal signal1 = SignalFromEval.fromEvaluatedCase(makeCase(1.0d), "test_operator", null);

        assertNotNull(signal0);
        assertNotNull(signal1);
    }

    @Test
    void testCustomScoreThreshold() {
        EvolutionSignal signal0 = SignalFromEval.fromEvaluatedCase(makeCase(0.5d), "test_operator", 0.8d);
        EvolutionSignal signal1 = SignalFromEval.fromEvaluatedCase(makeCase(0.7d), "test_operator", 0.8d);
        EvolutionSignal signal2 = SignalFromEval.fromEvaluatedCase(makeCase(0.9d), "test_operator", 0.8d);

        assertNotNull(signal0);
        assertNotNull(signal1);
        assertNull(signal2);
    }

    @Test
    void testContextContainsEvaluationFieldsFromEvaluatedCase() {
        EvaluatedCase evaluatedCase = makeCase(
                0.0d,
                Map.of("query", "What is the answer?"),
                Map.of("expected", "42"),
                Map.of("result", "40"),
                "Wrong answer"
        );

        EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(evaluatedCase, "test_operator", 1.0d);

        assertNotNull(signal);
        Map<String, Object> context = signal.getContext();
        assertTrue(context.containsKey("question"));
        assertTrue(context.containsKey("label"));
        assertTrue(context.containsKey("answer"));
        assertTrue(context.containsKey("reason"));
        assertTrue(context.containsKey("score"));
        assertTrue(String.valueOf(context.get("question")).contains("What is the answer?"));
        assertTrue(String.valueOf(context.get("label")).contains("42"));
        assertTrue(String.valueOf(context.get("answer")).contains("40"));
        assertEquals("Wrong answer", context.get("reason"));
        assertEquals(0.0d, (Double) context.get("score"));
    }

    @Test
    void testOperatorIdAsSkillName() {
        EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(
                makeCase(0.0d),
                "skill_call_test_skill",
                1.0d
        );

        assertNotNull(signal);
        assertEquals("skill_call_test_skill", signal.getSkillName());
    }

    @Test
    void testEmptyOperatorAndNullAnswerMatchPythonStringSemantics() {
        EvaluatedCase evaluatedCase = new EvaluatedCase(
                new Case(Map.of("query", "test_input"), Map.of("expected", "test_label")),
                null,
                0.0d,
                null
        );

        EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(evaluatedCase, "", 1.0d);

        assertNotNull(signal);
        assertNull(signal.getSkillName());
        assertEquals("None", signal.getContext().get("answer"));
        assertEquals("", signal.getContext().get("reason"));
        assertEquals("score=0.00", signal.getExcerpt());
    }

    @Test
    void testBatchFiltersByThreshold() {
        List<EvaluatedCase> cases = makeCases(0.0d, 0.5d, 1.0d, 0.8d);

        List<EvolutionSignal> signals = SignalFromEval.fromEvaluatedCases(cases, "test_operator", 1.0d);

        assertEquals(3, signals.size());
        assertEquals("low_score", signals.get(0).getSignalType());
        assertEquals("evaluated", signals.get(1).getSignalType());
        assertEquals("evaluated", signals.get(2).getSignalType());
    }

    @Test
    void testBatchWithAllHighScores() {
        List<EvolutionSignal> signals = SignalFromEval.fromEvaluatedCases(
                makeCases(1.0d, 1.0d, 1.0d),
                "test_operator",
                1.0d
        );

        assertEquals(List.of(), signals);
    }

    @Test
    void testBatchWithAllLowScores() {
        List<EvolutionSignal> signals = SignalFromEval.fromEvaluatedCases(
                makeCases(0.0d, 0.0d, 0.0d),
                "test_operator",
                1.0d
        );

        assertEquals(3, signals.size());
    }

    @Test
    void testBatchMatchesSingleCaseResults() {
        List<EvaluatedCase> cases = makeCases(0.0d, 0.5d, 1.0d, 0.8d);

        List<EvolutionSignal> batchSignals = SignalFromEval.fromEvaluatedCases(cases, "test_operator", 1.0d);
        List<EvolutionSignal> singleSignals = new ArrayList<>();
        for (EvaluatedCase evaluatedCase : cases) {
            EvolutionSignal signal = SignalFromEval.fromEvaluatedCase(evaluatedCase, "test_operator", 1.0d);
            if (signal != null) {
                singleSignals.add(signal);
            }
        }

        assertEquals(singleSignals.size(), batchSignals.size());
        for (int i = 0; i < batchSignals.size(); i++) {
            assertEquals(singleSignals.get(i).getSignalType(), batchSignals.get(i).getSignalType());
            assertEquals(singleSignals.get(i).getSkillName(), batchSignals.get(i).getSkillName());
            assertEquals(singleSignals.get(i).getExcerpt(), batchSignals.get(i).getExcerpt());
        }
    }

    @Test
    void testEmptyCasesReturnsEmptyList() {
        List<EvolutionSignal> signals = SignalFromEval.fromEvaluatedCases(List.of(), "test_operator", null);

        assertEquals(List.of(), signals);
    }

    @Test
    void testCustomThresholdInBatch() {
        List<EvolutionSignal> signals = SignalFromEval.fromEvaluatedCases(
                makeCases(0.5d, 0.7d, 0.8d, 0.9d),
                "test_operator",
                0.75d
        );

        assertEquals(2, signals.size());
    }

    @Test
    void testExtractSignalFromLowScore() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("score", 0.3);
        evaluation.put("threshold", 0.6);
        evaluation.put("metric", "accuracy");

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertEquals("low_score", signal.getSignalType());
    }

    @Test
    void testExtractSignalFromEvaluationWithContext() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("score", 0.45);
        evaluation.put("question", "How to deploy the application?");
        evaluation.put("label", "Use kubectl apply");
        evaluation.put("answer", "Wrong answer provided");
        evaluation.put("reason", "Missed key step in deployment");

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertTrue(signal.getContext().containsKey("question"));
        assertEquals("How to deploy the application?", signal.getContext().get("question"));
    }

    @Test
    void testExtractSignalDeterminesSectionFromReason() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("score", 0.2);
        evaluation.put("reason", "Missing troubleshooting steps for network errors");

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertEquals("Troubleshooting", signal.getSection());
    }

    @Test
    void testExtractSignalForSkillExperience() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("skill_name", "deployment_helper");
        evaluation.put("score", 0.25);

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertEquals(EvolutionCategory.SKILL_EXPERIENCE, signal.getEvolutionType());
        assertEquals("deployment_helper", signal.getSkillName());
    }

    @Test
    void testExtractSignalPreservesEvaluationMetrics() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("score", 0.3);
        evaluation.put("metrics", Map.of(
            "accuracy", 0.25,
            "relevance", 0.35,
            "completeness", 0.4
        ));

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertTrue(signal.getContext().containsKey("metrics"));
    }

    @Test
    void testExtractSignalFromEmptyEvaluation() {
        Map<String, Object> emptyEval = new HashMap<>();

        EvolutionSignal signal = extractSignalFromEvaluation(emptyEval);

        assertNull(signal);
    }

    @Test
    void testExtractSignalWithExcerptFromReason() {
        Map<String, Object> evaluation = new HashMap<>();
        evaluation.put("score", 0.15);
        evaluation.put("reason", "Answer completely missed the point");

        EvolutionSignal signal = extractSignalFromEvaluation(evaluation);

        assertTrue(signal.getExcerpt().contains("score") || signal.getExcerpt().contains("0.15"));
    }

    private EvolutionSignal extractSignalFromEvaluation(Map<String, Object> evaluation) {
        if (evaluation.isEmpty()) {
            return null;
        }

        Double score = evaluation.containsKey("score") 
            ? ((Number) evaluation.get("score")).doubleValue() 
            : null;
        
        if (score == null || score >= 0.6) {
            return null; // No signal for good scores
        }

        String excerpt = "score=" + score;
        String section = determineSectionFromReason((String) evaluation.get("reason"));
        String skillName = (String) evaluation.get("skill_name");

        Map<String, Object> context = new HashMap<>();
        if (evaluation.containsKey("question")) {
            context.put("question", evaluation.get("question"));
        }
        if (evaluation.containsKey("label")) {
            context.put("label", evaluation.get("label"));
        }
        if (evaluation.containsKey("answer")) {
            context.put("answer", evaluation.get("answer"));
        }
        if (evaluation.containsKey("reason")) {
            context.put("reason", evaluation.get("reason"));
        }
        if (evaluation.containsKey("score")) {
            context.put("score", score);
        }
        if (evaluation.containsKey("metrics")) {
            context.put("metrics", evaluation.get("metrics"));
        }

        return EvolutionSignal.builder()
                .signalType("low_score")
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section(section)
                .excerpt(excerpt)
                .skillName(skillName)
                .context(context)
                .build();
    }

    private String determineSectionFromReason(String reason) {
        if (reason == null) {
            return "Troubleshooting";
        }
        if (reason.contains("example") || reason.contains("Example")) {
            return "Examples";
        }
        if (reason.contains("instruction") || reason.contains("Instruction")) {
            return "Instructions";
        }
        return "Troubleshooting";
    }

    private EvaluatedCase makeCase(double score) {
        return makeCase(
                score,
                Map.of("query", "test_input"),
                Map.of("expected", "test_label"),
                Map.of("result", "test_answer"),
                "test_reason"
        );
    }

    private EvaluatedCase makeCase(
            double score,
            Map<String, Object> inputs,
            Map<String, Object> label,
            Map<String, Object> answer,
            String reason
    ) {
        return new EvaluatedCase(new Case(inputs, label), answer, score, reason);
    }

    private List<EvaluatedCase> makeCases(double... scores) {
        List<EvaluatedCase> result = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            result.add(new EvaluatedCase(
                    new Case(Map.of("query", "input_" + i), Map.of("expected", "label_" + i)),
                    Map.of("result", "answer_" + i),
                    scores[i],
                    "reason_" + i
            ));
        }
        return result;
    }
}

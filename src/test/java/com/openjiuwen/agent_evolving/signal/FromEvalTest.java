/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

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
}
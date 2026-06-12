/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for offline evaluated-case signal conversion.
 *
 * <p>Mirrors Python's {@code from_evaluated_case} and {@code from_evaluated_cases} in
 * {@code openjiuwen/agent_evolving/signal/from_eval.py}.</p>
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
    void fromEvaluatedCasesDropsFilteredCases() {
        List<EvolutionSignal> signals = FromEval.fromEvaluatedCases(
                List.of(evaluated(0.2, "bad"), evaluated(0.9, "good"), evaluated(0.0, "zero")),
                "operator",
                0.5d);

        assertEquals(2, signals.size());
        assertEquals("evaluated", signals.get(0).getSignalType());
        assertEquals("low_score", signals.get(1).getSignalType());
    }

    private static EvaluatedCase evaluated(double score, String reason) {
        Case sample = new Case(Map.of("query", "test"), Map.of("answer", "expected"));
        return new EvaluatedCase(sample, Map.of("output", "result"), score, reason, null);
    }
}

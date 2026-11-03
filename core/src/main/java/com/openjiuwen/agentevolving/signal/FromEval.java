/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline adapter from evaluated cases to evolution signals.
 *
 * <p>Mirrors Python's {@code from_evaluated_case} and {@code from_evaluated_cases} in
 * {@code openjiuwen/agent_evolving/signal/from_eval.py}.</p>
 */
public final class FromEval {

    private FromEval() {
    }

    public static EvolutionSignal fromEvaluatedCase(EvaluatedCase caseValue) {
        return fromEvaluatedCase(caseValue, "", null);
    }

    public static EvolutionSignal fromEvaluatedCase(EvaluatedCase caseValue, String operatorId) {
        return fromEvaluatedCase(caseValue, operatorId, null);
    }

    public static EvolutionSignal fromEvaluatedCase(
            EvaluatedCase caseValue,
            String operatorId,
            Double scoreThreshold
    ) {
        return scoreThreshold != null && caseValue.getScore() >= scoreThreshold
                ? null
                : buildSignal(caseValue, operatorId);
    }

    public static List<EvolutionSignal> fromEvaluatedCases(List<EvaluatedCase> cases) {
        return fromEvaluatedCases(cases, "", null);
    }

    public static List<EvolutionSignal> fromEvaluatedCases(
            List<EvaluatedCase> cases,
            String operatorId,
            Double scoreThreshold
    ) {
        List<EvolutionSignal> signals = new ArrayList<>();
        for (EvaluatedCase caseValue : cases) {
            EvolutionSignal signal = fromEvaluatedCase(caseValue, operatorId, scoreThreshold);
            if (signal != null) {
                signals.add(signal);
            }
        }
        return signals;
    }

    private static EvolutionSignal buildSignal(EvaluatedCase caseValue, String operatorId) {
        double score = caseValue.getScore();
        String signalType = score == 0.0d ? "low_score" : "evaluated";
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("question", String.valueOf(caseValue.getCase().getInputs()));
        context.put("label", String.valueOf(caseValue.getCase().getLabel()));
        context.put("answer", String.valueOf(caseValue.getAnswer()));
        context.put("reason", caseValue.getReason() != null ? caseValue.getReason() : "");
        context.put("score", score);

        return EvolutionSignals.makeEvolutionSignal(
                signalType,
                "Troubleshooting",
                String.format(Locale.ROOT, "score=%.2f", score),
                null,
                operatorId != null && !operatorId.isEmpty() ? operatorId : null,
                "offline_evaluation",
                context);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline signal adapter for EvaluatedCase to EvolutionSignal conversion.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.signal.from_eval}.
 */
public final class SignalFromEval {

    private SignalFromEval() {
        // Utility class
    }

    /**
     * Convert an offline EvaluatedCase to EvolutionSignal.
     *
     * @param case_           Evaluated case from offline evaluation
     * @param operatorId      Operator identifier to attach as skill_name
     * @param scoreThreshold  Minimum score to skip signal generation.
     *                        Null means no filtering.
     * @return EvolutionSignal if applicable, null if filtered out by threshold
     */
    public static EvolutionSignal fromEvaluatedCase(
            EvaluatedCase evaluatedCase,
            String operatorId,
            Double scoreThreshold) {

        if (scoreThreshold != null && evaluatedCase.getScore() >= scoreThreshold) {
            return null;
        }

        String signalType = evaluatedCase.getScore() == 0 ? "low_score" : "evaluated";

        EvolutionSignal signal = new EvolutionSignal();
        signal.setSignalType(signalType);
        signal.setEvolutionType(EvolutionCategory.SKILL_EXPERIENCE);
        signal.setSection("Troubleshooting");
        signal.setExcerpt("score=" + String.format("%.2f", evaluatedCase.getScore()));
        if (operatorId != null && !operatorId.isEmpty()) {
            signal.setSkillName(operatorId);
        }

        // Set context
        signal.setContext(new java.util.LinkedHashMap<>());
        signal.getContext().put("question", String.valueOf(evaluatedCase.getCase().getInputs()));
        signal.getContext().put("label", String.valueOf(evaluatedCase.getCase().getLabel()));
        signal.getContext().put("answer", String.valueOf(evaluatedCase.getAnswer()));
        signal.getContext().put("reason", evaluatedCase.getReason() != null ? evaluatedCase.getReason() : "");
        signal.getContext().put("score", evaluatedCase.getScore());

        return signal;
    }

    /**
     * Batch convert EvaluatedCase list to EvolutionSignal list.
     *
     * @param cases           List of evaluated cases
     * @param operatorId      Operator identifier to attach as skill_name
     * @param scoreThreshold  Minimum score to skip signal generation.
     *                        Null means no filtering.
     * @return List of EvolutionSignal
     */
    public static List<EvolutionSignal> fromEvaluatedCases(
            List<EvaluatedCase> cases,
            String operatorId,
            Double scoreThreshold) {

        List<EvolutionSignal> signals = new ArrayList<>();
        for (EvaluatedCase evaluatedCase : cases) {
            EvolutionSignal signal = fromEvaluatedCase(evaluatedCase, operatorId, scoreThreshold);
            if (signal != null) {
                signals.add(signal);
            }
        }
        return signals;
    }
}
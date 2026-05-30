/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        signal.setExcerpt(String.format(Locale.ROOT, "score=%.2f", evaluatedCase.getScore()));
        if (operatorId != null && !operatorId.isEmpty()) {
            signal.setSkillName(operatorId);
        }

        // Set context
        signal.setContext(new LinkedHashMap<>());
        signal.getContext().put("question", pythonString(evaluatedCase.getCase().getInputs()));
        signal.getContext().put("label", pythonString(evaluatedCase.getCase().getLabel()));
        signal.getContext().put("answer", pythonString(evaluatedCase.getAnswer()));
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

    private static String pythonString(Object value) {
        if (value instanceof Map<?, ?> map) {
            return pythonMapRepr(map);
        }
        if (value instanceof List<?> list) {
            return pythonListRepr(list);
        }
        if (value == null) {
            return "None";
        }
        return String.valueOf(value);
    }

    private static String pythonRepr(Object value) {
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Map<?, ?> map) {
            return pythonMapRepr(map);
        }
        if (value instanceof List<?> list) {
            return pythonListRepr(list);
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        return pythonString(value);
    }

    private static String pythonMapRepr(Map<?, ?> map) {
        StringBuilder result = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            result.append(pythonRepr(entry.getKey())).append(": ").append(pythonRepr(entry.getValue()));
            if (iterator.hasNext()) {
                result.append(", ");
            }
        }
        result.append("}");
        return result.toString();
    }

    private static String pythonListRepr(List<?> list) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(pythonRepr(list.get(i)));
        }
        result.append("]");
        return result.toString();
    }
}

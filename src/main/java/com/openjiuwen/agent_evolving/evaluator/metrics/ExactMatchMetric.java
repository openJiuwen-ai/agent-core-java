/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import java.util.Locale;
import java.util.Map;

/**
 * Exact match or normalized match: 1.0 if consistent, 0.0 otherwise.
 *
 * <p>When normalize=true, applies _normalize first before comparison.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics.exact_match.ExactMatchMetric}.
 */
public class ExactMatchMetric extends Metric {

    private final boolean normalize;

    /**
     * Create with default normalize=true.
     */
    public ExactMatchMetric() {
        this(true);
    }

    /**
     * Create with specified normalize setting.
     *
     * @param normalize Whether to normalize before comparison
     */
    public ExactMatchMetric(boolean normalize) {
        this.normalize = normalize;
    }

    @Override
    public String getName() {
        return "exact_match";
    }

    @Override
    public boolean isHigherIsBetter() {
        return true;
    }

    @Override
    public Double compute(Object prediction, Object label, Map<String, Object> kwargs) {
        if (normalize) {
            return normalize(prediction).equals(normalize(label))
                    ? 1.0 
                    : 0.0;
        }
        return pythonString(prediction).equals(pythonString(label)) ? 1.0 : 0.0;
    }

    /**
     * Convert to lowercase, strip whitespace, collapse multiple spaces to single space.
     *
     * @param inputData Input data to normalize
     * @return Normalized string
     */
    public static String normalize(String inputData) {
        return String.valueOf(inputData).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String normalize(Object inputData) {
        return pythonString(inputData).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String pythonString(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        return String.valueOf(value);
    }
}

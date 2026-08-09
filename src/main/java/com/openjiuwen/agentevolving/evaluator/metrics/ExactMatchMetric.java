/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import java.util.Locale;
import java.util.Map;

/**
 * Exact match or normalized match metric.
 * <p>
 * Mirrors Python's {@code ExactMatchMetric} in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/exact_match.py}.
 */
public class ExactMatchMetric extends Metric {

    private final boolean normalize;

    public ExactMatchMetric() {
        this(true);
    }

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
    public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
        if (normalize) {
            return normalize(prediction).equals(normalize(label)) ? 1.0 : 0.0;
        }
        return pythonString(prediction).equals(pythonString(label)) ? 1.0 : 0.0;
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

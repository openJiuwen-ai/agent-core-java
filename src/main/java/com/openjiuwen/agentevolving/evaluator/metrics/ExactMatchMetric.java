/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.metrics;

import java.util.Locale;
import java.util.Map;

/**
 * Exact match or normalized match: 1.0 if consistent, 0.0 otherwise.
 * <p>
 * When normalize=true, applies _normalize first before comparison.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics.exact_match.ExactMatchMetric}.
 * 
 * @since 0.1.7
 */
public class ExactMatchMetric extends Metric {
    private final boolean normalize;

    /**
     * Create with default normalize=true.
     * 
     * @since 0.1.7
     */
    public ExactMatchMetric() {
        this(true);
    }

    /**
     * Create with specified normalize setting.
     * 
     * @param normalize Whether to normalize before comparison
     * @since 0.1.7
     */
    public ExactMatchMetric(boolean normalize) {
        this.normalize = normalize;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "exact_match";
    }

    /**
     * isHigherIsBetter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isHigherIsBetter() {
        return true;
    }

    /**
     * compute.
     * 
     * @param prediction prediction
     * @param label label
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Double compute(Object prediction, Object label, Map<String, Object> kwargs) {
        if (normalize) {
            return normalize(String.valueOf(prediction)).equals(normalize(String.valueOf(label))) ? 1.0 : 0.0;
        }
        return String.valueOf(prediction).equals(String.valueOf(label)) ? 1.0 : 0.0;
    }

    /**
     * Convert to lowercase, strip whitespace, collapse multiple spaces to single space.
     * 
     * @param inputData Input data to normalize
     * @return Normalized string
     * @since 0.1.7
     */
    public static String normalize(String inputData) {
        return String.valueOf(inputData).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

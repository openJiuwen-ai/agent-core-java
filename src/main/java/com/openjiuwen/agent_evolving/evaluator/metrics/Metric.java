// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.evaluator.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for evaluation metrics.
 *
 * <p>Subclasses implement compute() for scoring logic.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.metrics.base.Metric}.
 */
public abstract class Metric {

    /**
     * Metric identifier.
     *
     * @return Unique metric name
     */
    public abstract String getName();

    /**
     * Whether higher score indicates better performance.
     *
     * @return True if higher is better (default: True)
     */
    public boolean isHigherIsBetter() {
        return true;
    }

    /**
     * Compute score for single sample.
     *
     * @param prediction Model prediction
     * @param label      Expected label
     * @param kwargs     Additional context (e.g., question, case)
     * @return Score (float) or Map of metric_name to score
     */
    public abstract Object compute(Object prediction, Object label, Map<String, Object> kwargs);

    /**
     * Compute score for single sample (simplified).
     *
     * @param prediction Model prediction
     * @param label      Expected label
     * @return Score value
     */
    public Object compute(Object prediction, Object label) {
        return compute(prediction, label, new java.util.HashMap<>());
    }

    /**
     * Compute scores for batch of samples.
     *
     * @param predictions List of predictions
     * @param labels      List of labels
     * @param kwargs      Additional context
     * @return List of metric results
     */
    public List<Object> computeBatch(List<?> predictions, List<?> labels, Map<String, Object> kwargs) {
        List<Object> results = new ArrayList<>();
        int size = Math.min(predictions != null ? predictions.size() : 0, labels != null ? labels.size() : 0);
        for (int i = 0; i < size; i++) {
            results.add(compute(predictions.get(i), labels.get(i), kwargs != null ? kwargs : Map.of()));
        }
        return results;
    }
}

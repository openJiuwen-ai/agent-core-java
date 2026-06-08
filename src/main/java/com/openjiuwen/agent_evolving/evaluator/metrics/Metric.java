/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Metric abstraction for scoring.
 * <p>
 * Mirrors Python's {@code Metric} in
 * {@code openjiuwen/agent_evolving/evaluator/metrics/base.py}.
 */
public abstract class Metric {

    public abstract String getName();

    public boolean isHigherIsBetter() {
        return true;
    }

    public abstract Object compute(Object prediction, Object label, Map<String, Object> kwargs);

    public List<Object> computeBatch(List<?> predictions, List<?> labels, Map<String, Object> kwargs) {
        int size = Math.min(predictions != null ? predictions.size() : 0, labels != null ? labels.size() : 0);
        List<Object> results = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            results.add(compute(predictions.get(index), labels.get(index), kwargs));
        }
        return results;
    }
}

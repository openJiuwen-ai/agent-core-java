/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates sub-workflow stream frames and builds the final output map.
 *
 * <p>Mirrors Python's {@code SubWorkflowStreamState} in
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}.</p>
 */
public class SubWorkflowStreamState {

    private final List<Map<String, ?>> accumulatedOutputs = new ArrayList<>();

    public void accumulate(Map<String, ?> output) {
        if (output != null) {
            accumulatedOutputs.add(output);
        }
    }

    public Map<String, ?> buildFinalResult() {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map<String, ?> output : accumulatedOutputs) {
            Object inner = output.containsKey("output") ? output.get("output") : output;
            if (inner instanceof Map<?, ?> innerMap) {
                for (Map.Entry<?, ?> entry : innerMap.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        merged.merge(key, entry.getValue(), SubWorkflowStreamState::mergeValues);
                    }
                }
            }
        }
        return merged;
    }

    public void clear() {
        accumulatedOutputs.clear();
    }

    private static Object mergeValues(Object left, Object right) {
        if (left instanceof String || right instanceof String) {
            return String.valueOf(left) + right;
        }
        if (left instanceof List<?> leftList) {
            List<Object> values = new ArrayList<>(leftList);
            if (right instanceof List<?> rightList) {
                values.addAll(rightList);
            } else {
                values.add(right);
            }
            return values;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return leftNumber.doubleValue() + rightNumber.doubleValue();
        }
        return right;
    }
}

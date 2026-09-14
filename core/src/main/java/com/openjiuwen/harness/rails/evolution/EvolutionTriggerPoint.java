/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Evolution trigger points.
 *
 * <p>Mirrors Python's {@code EvolutionTriggerPoint} in
 * {@code openjiuwen/harness/rails/evolution/evolution_rail.py}.</p>
 */
public enum EvolutionTriggerPoint {
    AFTER_INVOKE("after_invoke"),
    AFTER_MODEL_CALL("after_model_call"),
    AFTER_TOOL_CALL("after_tool_call"),
    AFTER_TASK_ITERATION("after_task_iteration"),
    NONE("none");

    private final String value;

    EvolutionTriggerPoint(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EvolutionTriggerPoint fromValue(String value) {
        for (EvolutionTriggerPoint triggerPoint : values()) {
            if (triggerPoint.value.equals(value)) {
                return triggerPoint;
            }
        }
        throw new IllegalArgumentException("Unknown evolution trigger point: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

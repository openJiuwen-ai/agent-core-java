/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

/**
 * Public enum EvolutionTriggerPoint used by the Java parity implementation.
 * 
 * @since 0.1.7
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

    /**
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String value() {
        return value;
    }
}

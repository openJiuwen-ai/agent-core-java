/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

/**
 * Trigger points for automatic evolution execution.
 *
 * <p>Mirrors Python's {@code EvolutionTriggerPoint} in
 * {@code openjiuwen.harness.rails.evolution.evolution_rail}.</p>
 */
public enum EvolutionTriggerPoint {
    AFTER_INVOKE,
    AFTER_MODEL_CALL,
    AFTER_TOOL_CALL,
    AFTER_TASK_ITERATION,
    NONE
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

/**
 * Evolution trigger points.
 *
 * <p>Mirrors Python's {@code EvolutionTriggerPoint} in
 * {@code openjiuwen/harness/rails/evolution/evolution_rail.py}.</p>
 */
public enum EvolutionTriggerPoint {
    AFTER_INVOKE,
    AFTER_MODEL_CALL,
    AFTER_TOOL_CALL,
    AFTER_TASK_ITERATION,
    NONE
}

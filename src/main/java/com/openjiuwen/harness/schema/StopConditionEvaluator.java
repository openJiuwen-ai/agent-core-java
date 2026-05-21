/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Strategy interface for a single stop condition.
 *
 * <p>Implement {@code shouldStop()} to return {@code true} when the
 * outer task loop should terminate.
 *
 * <p>Mirrors Python's {@code StopConditionEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public interface StopConditionEvaluator {

    /**
     * Evaluator name used as stop_reason.
     */
    String getName();

    /**
     * Return true if the loop should stop.
     *
     * @param ctx Current evaluation context.
     * @return True to stop the loop, false to continue.
     */
    boolean shouldStop(StopEvaluationContext ctx);

    /**
     * Reset internal state for a new invoke cycle.
     */
    default void reset() {
        // Default empty implementation
    }

    /**
     * Export serializable state snapshot.
     *
     * @return A JSON-safe map, or null if no state to save.
     */
    default Map<String, Object> getState() {
        return null;
    }

    /**
     * Restore state from a persisted snapshot.
     *
     * @param data Previously exported state map.
     */
    default void loadState(Map<String, Object> data) {
        // Default empty implementation
    }
}
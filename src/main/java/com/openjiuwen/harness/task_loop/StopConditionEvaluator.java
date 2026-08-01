/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public interface StopConditionEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface StopConditionEvaluator {
    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    String name();

    /**
     * shouldStop.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    boolean shouldStop(StopEvaluationContext context);

    /**
     * reset.
     * 
     * @since 0.1.7
     */
    default void reset() {
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    default java.util.Map<String, Object> getState() {
        return java.util.Map.of();
    }

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
     */
    default void loadState(java.util.Map<String, Object> state) {
    }
}

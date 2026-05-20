/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public interface StopConditionEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface StopConditionEvaluator {
    String name();
    boolean shouldStop(StopEvaluationContext context);

    default void reset() {
    }

    default java.util.Map<String, Object> getState() {
        return java.util.Map.of();
    }

    default void loadState(java.util.Map<String, Object> state) {
    }
}

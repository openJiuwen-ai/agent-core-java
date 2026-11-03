/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.Map;

/**
 * Mirrors Python's {@code StopConditionEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public interface StopConditionEvaluator {

    default String getName() {
        return getClass().getSimpleName();
    }

    boolean shouldStop(StopEvaluationContext ctx);

    default void reset() {
    }

    default Map<String, Object> getState() {
        return null;
    }

    default void loadState(Map<String, Object> data) {
    }
}

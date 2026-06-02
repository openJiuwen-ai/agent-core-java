/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Stop when wall-clock elapsed time exceeds a limit.
 *
 * <p>Mirrors Python's {@code TimeoutEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public class TimeoutEvaluator implements StopConditionEvaluator {

    private final double timeoutSeconds;

    public TimeoutEvaluator(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx != null && ctx.getElapsedSeconds() >= timeoutSeconds;
    }
}

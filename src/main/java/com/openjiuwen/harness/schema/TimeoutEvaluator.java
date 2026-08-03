/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Mirrors Python's {@code TimeoutEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class TimeoutEvaluator implements StopConditionEvaluator {

    private final double timeoutSeconds;

    public TimeoutEvaluator(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx.getElapsedSeconds() >= timeoutSeconds;
    }
}

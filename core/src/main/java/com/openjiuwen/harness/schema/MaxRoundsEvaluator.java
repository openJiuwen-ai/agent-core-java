/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Mirrors Python's {@code MaxRoundsEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class MaxRoundsEvaluator implements StopConditionEvaluator {

    private final int maxRounds;

    public MaxRoundsEvaluator(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx.getIteration() >= maxRounds;
    }
}

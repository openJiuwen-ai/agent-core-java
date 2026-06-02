/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Stop after a fixed number of completed outer-loop rounds.
 *
 * <p>Mirrors Python's {@code MaxRoundsEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public class MaxRoundsEvaluator implements StopConditionEvaluator {

    private final int maxRounds;

    public MaxRoundsEvaluator(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx != null && ctx.getIteration() >= maxRounds;
    }
}

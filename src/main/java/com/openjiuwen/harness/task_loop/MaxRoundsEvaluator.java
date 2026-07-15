/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class MaxRoundsEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class MaxRoundsEvaluator implements StopConditionEvaluator {
    private final int maxRounds;

    /**
     * MaxRoundsEvaluator.
     * 
     * @param maxRounds maxRounds
     * @since 0.1.7
     */
    public MaxRoundsEvaluator(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "MaxRounds";
    }

    /**
     * shouldStop.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shouldStop(StopEvaluationContext context) {
        return context != null && context.getIteration() >= maxRounds;
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of("max_rounds", maxRounds);
    }
}

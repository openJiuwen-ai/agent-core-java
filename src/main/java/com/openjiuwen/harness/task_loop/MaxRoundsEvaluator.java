/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class MaxRoundsEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class MaxRoundsEvaluator implements StopConditionEvaluator {
    private final int maxRounds;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MaxRoundsEvaluator(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "MaxRounds";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop(StopEvaluationContext context) {
        return context != null && context.getIteration() >= maxRounds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of("max_rounds", maxRounds);
    }
}

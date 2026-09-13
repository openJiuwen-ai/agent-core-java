/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class TokenBudgetEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TokenBudgetEvaluator implements StopConditionEvaluator {
    private final int maxTokens;

    /**
     * TokenBudgetEvaluator.
     * 
     * @param maxTokens maxTokens
     * @since 0.1.7
     */
    public TokenBudgetEvaluator(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "TokenBudget";
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
        return context != null && context.getTokenUsage() >= maxTokens;
    }
}

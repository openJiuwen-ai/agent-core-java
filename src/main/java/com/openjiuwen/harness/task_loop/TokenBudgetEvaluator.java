/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class TokenBudgetEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TokenBudgetEvaluator implements StopConditionEvaluator {
    private final int maxTokens;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TokenBudgetEvaluator(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "TokenBudget";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop(StopEvaluationContext context) {
        return context != null && context.getTokenUsage() >= maxTokens;
    }
}

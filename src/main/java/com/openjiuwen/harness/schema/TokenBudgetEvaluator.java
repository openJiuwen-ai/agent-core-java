/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Stop when cumulative token usage reaches a configured budget.
 *
 * <p>Mirrors Python's {@code TokenBudgetEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public class TokenBudgetEvaluator implements StopConditionEvaluator {

    private final int maxTokens;

    public TokenBudgetEvaluator(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx != null && ctx.getTokenUsage() >= maxTokens;
    }
}

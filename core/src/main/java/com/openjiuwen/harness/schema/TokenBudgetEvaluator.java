/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Mirrors Python's {@code TokenBudgetEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class TokenBudgetEvaluator implements StopConditionEvaluator {

    private final int maxTokens;

    public TokenBudgetEvaluator(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return ctx.getTokenUsage() >= maxTokens;
    }
}

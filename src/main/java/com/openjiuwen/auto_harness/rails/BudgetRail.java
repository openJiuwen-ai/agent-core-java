/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.infra.SessionBudgetController;

import java.util.logging.Logger;

/**
 * Budget rail — session time + API cost + CI gate logging.
 *
 * <p>Mirrors Python's {@code BudgetRail} in {@code openjiuwen.auto_harness.rails.budget_rail}.</p>
 */
public class BudgetRail {

    private static final Logger logger = Logger.getLogger(BudgetRail.class.getName());
    private static final double INPUT_COST_PER_TOKEN = 3e-6;
    private static final double OUTPUT_COST_PER_TOKEN = 15e-6;

    private final SessionBudgetController budget;

    public BudgetRail(SessionBudgetController budget) {
        this.budget = budget;
    }

    /**
     * Check budget before each tool call.
     *
     * @param ctx the agent callback context
     */
    public void beforeToolCall(Object ctx) {
        if (budget.isShouldStop()) {
            logger.warning("Session budget exceeded");
            // Request force finish (placeholder)
        }
    }

    /**
     * Estimate cost from model response usage.
     *
     * @param ctx the agent callback context
     */
    public void afterModelCall(Object ctx) {
        // TODO: Implement cost estimation from response usage
    }

    /**
     * Get the budget controller.
     *
     * @return the budget controller
     */
    public SessionBudgetController getBudget() {
        return budget;
    }
}
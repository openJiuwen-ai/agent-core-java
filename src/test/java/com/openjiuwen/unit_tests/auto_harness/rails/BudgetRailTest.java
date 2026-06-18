/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.rails;

import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.auto_harness.rails.BudgetRail;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.ModelCallInputs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code BudgetRail} in
 * {@code openjiuwen/auto_harness/rails/budget_rail.py}.
 */
class BudgetRailTest {

    @Test
    void beforeToolCallStopsWhenBudgetExceeded() {
        SessionBudgetController budget = new SessionBudgetController(3600.0, 1.0, 1200.0);
        budget.start();
        budget.addCost(2.0);
        BudgetRail rail = new BudgetRail(budget);
        AgentCallbackContext ctx = new AgentCallbackContext();

        rail.beforeToolCall(ctx).toCompletableFuture().join();

        assertThat(ctx.hasForceFinishRequest()).isTrue();
    }

    @Test
    void afterModelCallAddsUsageCost() {
        SessionBudgetController budget = new SessionBudgetController(3600.0, 0.001, 1200.0);
        budget.start();
        BudgetRail rail = new BudgetRail(budget);
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setResponse(Map.of("usage", Map.of("input_tokens", 1000, "output_tokens", 1000)));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setInputs(inputs);

        rail.afterModelCall(ctx).toCompletableFuture().join();

        assertThat(ctx.hasForceFinishRequest()).isTrue();
    }

    @Test
    void taskIterationHooksRecordCiGateState() {
        BudgetRail rail = new BudgetRail(new SessionBudgetController());
        AgentCallbackContext ctx = new AgentCallbackContext();

        rail.beforeTaskIteration(ctx).toCompletableFuture().join();
        rail.afterTaskIteration(ctx).toCompletableFuture().join();

        assertThat(ctx.getExtra())
                .containsEntry("ci_gate_iteration_started", true)
                .containsEntry("ci_gate_iteration_complete", true);
    }
}

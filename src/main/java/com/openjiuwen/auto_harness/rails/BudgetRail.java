/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Monitors session time, API cost, and CI iteration hooks.
 *
 * <p>Mirrors Python's {@code BudgetRail} in
 * {@code openjiuwen/auto_harness/rails/budget_rail.py}.</p>
 */
public class BudgetRail extends AgentRail {
    private static final Logger LOGGER = Logger.getLogger(BudgetRail.class.getName());
    private static final double INPUT_COST_PER_TOKEN = 3e-6;
    private static final double OUTPUT_COST_PER_TOKEN = 15e-6;

    private final SessionBudgetController budget;

    public BudgetRail(SessionBudgetController budget) {
        this.budget = budget;
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        if (budget.isShouldStop()) {
            LOGGER.warning("Session budget exceeded");
            context.requestForceFinish(Map.of("reason", "Session budget exceeded"));
        }
        return completed();
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof ModelCallInputs inputs) || inputs.getResponse() == null) {
            return completed();
        }
        Object usage = readProperty(inputs.getResponse(), "usage");
        if (usage == null) {
            return completed();
        }
        double inputTokens = readNumber(usage, "input_tokens", "inputTokens");
        double outputTokens = readNumber(usage, "output_tokens", "outputTokens");
        double cost = inputTokens * INPUT_COST_PER_TOKEN + outputTokens * OUTPUT_COST_PER_TOKEN;
        if (cost > 0.0d) {
            budget.addCost(cost);
            LOGGER.fine("API cost +$" + cost);
        }
        if (budget.isShouldStop()) {
            LOGGER.warning("Cost budget exceeded");
            context.requestForceFinish(Map.of("reason", "Cost budget exceeded"));
        }
        return completed();
    }

    @Override
    public CompletionStage<Void> beforeTaskIteration(AgentCallbackContext context) {
        LOGGER.info("CI gate rail: iteration starting");
        context.getExtra().put("ci_gate_iteration_started", true);
        return completed();
    }

    @Override
    public CompletionStage<Void> afterTaskIteration(AgentCallbackContext context) {
        LOGGER.info("CI gate rail: iteration complete");
        context.getExtra().put("ci_gate_iteration_complete", true);
        return completed();
    }

    private static double readNumber(Object target, String snakeName, String camelName) {
        Object value = readProperty(target, snakeName);
        if (value == null) {
            value = readProperty(target, camelName);
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    private static Object readProperty(Object target, String name) {
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[] {"get" + suffix, name}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Keep Python-like dynamic tolerance for usage snapshots.
            }
        }
        return null;
    }
}

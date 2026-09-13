/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.autoharness.infra.SessionBudgetController;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskIterationRail;
import com.openjiuwen.harness.task_loop.TaskIterationContext;

import java.util.Map;

/**
 * Budget rail: monitors session time/cost and records CI iteration boundaries.
 * 
 * @since 0.1.7
 */
public class BudgetRail extends DeepAgentRail implements TaskIterationRail {
    private static final double INPUT_COST_PER_TOKEN = 3e-6;
    private static final double OUTPUT_COST_PER_TOKEN = 15e-6;

    private final SessionBudgetController budget;
    private int iterationStarts;
    private int iterationCompletions;

    /**
     * BudgetRail.
     * 
     * @param budget budget
     * @since 0.1.7
     */
    public BudgetRail(SessionBudgetController budget) {
        this.budget = budget;
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (budget.shouldStop()) {
            ctx.requestForceFinish(Map.of("reason", "Session budget exceeded"));
        }
    }

    /**
     * afterModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs) || inputs.getResponse() == null) {
            return;
        }
        UsageMetadata usage = usageMetadata(inputs.getResponse());
        if (usage == null) {
            return;
        }
        double cost = usage.getInputTokens() * INPUT_COST_PER_TOKEN + usage.getOutputTokens() * OUTPUT_COST_PER_TOKEN;
        if (cost > 0) {
            budget.addCost(cost);
        }
        if (budget.shouldStop()) {
            ctx.requestForceFinish(Map.of("reason", "Cost budget exceeded"));
        }
    }

    /**
     * beforeTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        iterationStarts++;
    }

    /**
     * afterTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterTaskIteration(TaskIterationContext ctx) {
        iterationCompletions++;
    }

    /**
     * iterationStarts.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int iterationStarts() {
        return iterationStarts;
    }

    /**
     * iterationCompletions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int iterationCompletions() {
        return iterationCompletions;
    }

    /**
     * usageMetadata.
     * 
     * @param response response
     * @return the result
     * @since 0.1.7
     */
    private static UsageMetadata usageMetadata(Object response) {
        if (response instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getUsageMetadata();
        }
        if (response instanceof Map<?, ?> map) {
            return TaskIterationContext.usageMetadataFrom((Map<String, Object>) map);
        }
        return null;
    }
}

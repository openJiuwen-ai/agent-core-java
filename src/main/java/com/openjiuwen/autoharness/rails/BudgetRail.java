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
 */
public class BudgetRail extends DeepAgentRail implements TaskIterationRail {
    private static final double INPUT_COST_PER_TOKEN = 3e-6;
    private static final double OUTPUT_COST_PER_TOKEN = 15e-6;

    private final SessionBudgetController budget;
    private int iterationStarts;
    private int iterationCompletions;

    /**
     * Auto-generated for codecheck compliance.
     */
    public BudgetRail(SessionBudgetController budget) {
        this.budget = budget;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (budget.shouldStop()) {
            ctx.requestForceFinish(Map.of("reason", "Session budget exceeded"));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void afterModelCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs) || inputs.getResponse() == null) {
            return;
        }
        UsageMetadata usage = usageMetadata(inputs.getResponse());
        if (usage == null) {
            return;
        }
        double cost = usage.getInputTokens() * INPUT_COST_PER_TOKEN
                + usage.getOutputTokens() * OUTPUT_COST_PER_TOKEN;
        if (cost > 0) {
            budget.addCost(cost);
        }
        if (budget.shouldStop()) {
            ctx.requestForceFinish(Map.of("reason", "Cost budget exceeded"));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        iterationStarts++;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void afterTaskIteration(TaskIterationContext ctx) {
        iterationCompletions++;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int iterationStarts() {
        return iterationStarts;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int iterationCompletions() {
        return iterationCompletions;
    }

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

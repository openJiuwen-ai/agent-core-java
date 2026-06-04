/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Budget rail — session time + API cost + CI gate logging.
 *
 * <p>Mirrors Python's {@code BudgetRail} in {@code openjiuwen.auto_harness.rails.budget_rail}.</p>
 */
public class BudgetRail extends DeepAgentRail {

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
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        beforeToolCall((Object) ctx);
    }

    public void beforeToolCall(Object ctx) {
        if (budget.isShouldStop()) {
            logger.warning("Session budget exceeded");
            markForceFinish(ctx, "Session budget exceeded");
        }
    }

    /**
     * Estimate cost from model response usage.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        afterModelCall((Object) ctx);
    }

    public void afterModelCall(Object ctx) {
        ModelCallInputs inputs = modelInputs(ctx);
        if (inputs == null || inputs.getResponse() == null) {
            return;
        }

        Usage usage = extractUsage(inputs.getResponse());
        if (usage == null) {
            return;
        }

        double cost = usage.inputTokens() * INPUT_COST_PER_TOKEN
                + usage.outputTokens() * OUTPUT_COST_PER_TOKEN;
        if (cost > 0.0) {
            budget.addCost(cost);
            logger.fine(String.format("API cost +$%.6f", cost));
        }

        if (budget.isShouldStop()) {
            logger.warning("Cost budget exceeded");
            markForceFinish(ctx, "Cost budget exceeded");
        }
    }

    /**
     * Get the budget controller.
     *
     * @return the budget controller
     */
    public SessionBudgetController getBudget() {
        return budget;
    }

    /**
     * Log CI gate iteration start.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        beforeTaskIteration((Object) ctx);
    }

    public void beforeTaskIteration(Object ctx) {
        logger.info("CI gate rail: iteration starting");
    }

    /**
     * Log CI gate iteration completion.
     *
     * @param ctx the agent callback context
     */
    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        afterTaskIteration((Object) ctx);
    }

    public void afterTaskIteration(Object ctx) {
        logger.info("CI gate rail: iteration complete");
    }

    private static ModelCallInputs modelInputs(Object ctx) {
        if (ctx instanceof AgentCallbackContext callbackContext
                && callbackContext.getInputs() instanceof ModelCallInputs inputs) {
            return inputs;
        }
        if (ctx instanceof ModelCallInputs inputs) {
            return inputs;
        }
        return null;
    }

    private static Usage extractUsage(Object response) {
        if (response instanceof UsageMetadata metadata) {
            return new Usage(metadata.getInputTokens(), metadata.getOutputTokens());
        }
        Object usage = readProperty(response, "usageMetadata");
        if (usage == null) {
            usage = readProperty(response, "usage");
        }
        if (usage instanceof UsageMetadata metadata) {
            return new Usage(metadata.getInputTokens(), metadata.getOutputTokens());
        }
        if (usage instanceof Map<?, ?> map) {
            return new Usage(readInt(map, "input_tokens", "inputTokens", "prompt_tokens"),
                    readInt(map, "output_tokens", "outputTokens", "completion_tokens"));
        }
        if (response instanceof Map<?, ?> map) {
            return new Usage(readInt(map, "input_tokens", "inputTokens", "prompt_tokens"),
                    readInt(map, "output_tokens", "outputTokens", "completion_tokens"));
        }
        return null;
    }

    private static Object readProperty(Object target, String propertyName) {
        if (target == null) {
            return null;
        }
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (String methodName : List.of("get" + suffix, propertyName)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor shape.
            }
        }
        return null;
    }

    private static int readInt(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // Try next key.
                }
            }
        }
        return 0;
    }

    private static void markForceFinish(Object ctx, String reason) {
        if (ctx instanceof AgentCallbackContext callbackContext) {
            callbackContext.getExtra().put("force_finish", Map.of("reason", reason));
        }
    }

    private record Usage(int inputTokens, int outputTokens) {}
}

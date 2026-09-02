/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.llm_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Base class for LLMCall dimension optimizers.
 * <p>
 * Only optimizes Operators exposing system_prompt/user_prompt.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call.base.LLMCallOptimizerBase}.
 * 
 * @since 0.1.7
 */
public abstract class LLMCallOptimizerBase extends BaseOptimizer {
    /**
     * LLMCallOptimizerBase.
     * 
     * @since 0.1.7
     */
    protected LLMCallOptimizerBase() {
        this.domain = "llm";
    }

    /**
     * defaultTargets.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> defaultTargets() {
        return Arrays.asList("system_prompt", "user_prompt");
    }

    /**
     * Check if target is frozen based on get_tunables.
     * 
     * @param op Operator instance
     * @param target Target name
     * @return True if target is frozen
     * @since 0.1.7
     */
    protected boolean isTargetFrozen(Object op, String target) {
        try {
            return !extractTunableNames(op).contains(target);
        } catch (ReflectiveOperationException e) {
            // Ignore
        }
        return true;
    }

    /**
     * getPromptTemplate.
     * 
     * @param op op
     * @param target target
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    protected PromptTemplate getPromptTemplate(Object op, String target) {
        try {
            java.lang.reflect.Method method = op.getClass().getMethod("getState");
            Object state = method.invoke(op);
            if (state instanceof Map) {
                Object content = ((Map<String, Object>) state).get(target);
                return PromptTemplate.builder().content(content != null ? String.valueOf(content) : "").build();
            }
        } catch (ReflectiveOperationException e) {
            // Ignore
        }
        return PromptTemplate.builder().content("").build();
    }
}

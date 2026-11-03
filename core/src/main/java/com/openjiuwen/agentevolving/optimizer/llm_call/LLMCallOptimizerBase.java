/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.llm_call;

import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.Operator;

import java.util.List;
import java.util.Map;

/**
 * Base class for LLM-call dimension optimizers.
 *
 * <p>Mirrors Python's {@code LLMCallOptimizerBase} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/base.py}.</p>
 */
public abstract class LLMCallOptimizerBase extends BaseOptimizer {

    public static final String SYSTEM_PROMPT = "system_prompt";
    public static final String USER_PROMPT = "user_prompt";

    protected LLMCallOptimizerBase() {
        this.domain = "llm";
    }

    /**
     * Default prompt targets for LLM-call optimization.
     *
     * @return system and user prompt targets
     */
    @Override
    public List<String> defaultTargets() {
        return List.of(SYSTEM_PROMPT, USER_PROMPT);
    }

    /**
     * Preserve BaseOptimizer filtering and logging semantics.
     *
     * @param operators candidate operators
     * @param targets tunable names
     * @return filtered operators
     */
    public static Map<String, Operator> filterOperators(Map<String, Operator> operators, List<String> targets) {
        return BaseOptimizer.filterOperators(operators, targets);
    }

    /**
     * Check if the target is frozen by looking at exposed tunables.
     *
     * @param operator operator under optimization
     * @param target target tunable name
     * @return true when target is not tunable
     */
    protected boolean isTargetFrozen(Operator operator, String target) {
        return operator == null || !operator.getTunables().containsKey(target);
    }

    /**
     * Build a PromptTemplate from operator state.
     *
     * @param operator operator exposing state
     * @param target target key in state
     * @return prompt template wrapping current content, or empty content when missing
     */
    protected PromptTemplate getPromptTemplate(Operator operator, String target) {
        Map<String, Object> state = operator == null ? Map.of() : operator.getState();
        Object content = state == null ? "" : state.getOrDefault(target, "");
        return PromptTemplate.builder().content(content == null ? "" : content).build();
    }
}

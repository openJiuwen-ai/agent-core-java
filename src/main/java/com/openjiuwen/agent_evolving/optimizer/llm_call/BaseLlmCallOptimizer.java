// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.llm_call;

/**
 * Legacy compatibility base for Java LLM call optimizers.
 *
 * <p>The Python LLM call optimizer base is implemented by {@link LLMCallOptimizerBase}; this type preserves the
 * older Java call-context extension point for downstream code.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call.base} module at the compatibility boundary.
 */
@Deprecated(since = "0.1.12")
public abstract class BaseLlmCallOptimizer {
    
    protected final String optimizerType;
    
    public BaseLlmCallOptimizer(String optimizerType) {
        this.optimizerType = optimizerType;
    }
    
    /**
     * Optimize an LLM call context.
     *
     * @param callContext Java-specific call context
     * @return Optimized call context or optimization result
     */
    public abstract Object optimizeLlmCall(Object callContext);
    
    /**
     * Get optimizer type.
     */
    public String getOptimizerType() {
        return optimizerType;
    }
}

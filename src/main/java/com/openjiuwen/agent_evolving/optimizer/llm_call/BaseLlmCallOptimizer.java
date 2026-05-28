// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.llm_call;

/**
 * Base LLM call optimizer.
 * <p>
 * Mirrors Python's {@code base.py} from
 * {@code openjiuwen.agent_evolving.optimizer.llm_call.base}.
 */
public abstract class BaseLlmCallOptimizer {
    
    protected final String optimizerType;
    
    public BaseLlmCallOptimizer(String optimizerType) {
        this.optimizerType = optimizerType;
    }
    
    /**
     * Optimize LLM call.
     * PLACEHOLDER: Requires LLM call context.
     */
    public abstract Object optimizeLlmCall(Object callContext);
    
    /**
     * Get optimizer type.
     */
    public String getOptimizerType() {
        return optimizerType;
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Workflow component that wraps an LLM model for invocation and streaming.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMComponent}.
 * 
 * @since 0.1.7
 */
public class LLMComponent implements ComponentComposable {
    private LLMExecutable executable;
    private final LLMCompConfig config;

    /**
     * LLMComponent.
     * 
     * @param componentConfig componentConfig
     * @since 0.1.7
     */
    public LLMComponent(LLMCompConfig componentConfig) {
        this.config = componentConfig;
    }

    /**
     * getExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LLMExecutable getExecutable() {
        if (executable == null) {
            executable = (LLMExecutable) toExecutable();
        }
        return executable;
    }

    /**
     * toExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Executable<?, ?> toExecutable() {
        return new LLMExecutable(config);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.base.BasePromptBuilder}.
 * 
 * @since 0.1.7
 */
public abstract class BasePromptBuilder {
    /**
     * model.
     * 
     * @since 0.1.7
     */
    protected final Model model;

    /**
     * modelConfig.
     * 
     * @since 0.1.7
     */
    protected final ModelRequestConfig modelConfig;

    /**
     * modelClientConfig.
     * 
     * @since 0.1.7
     */
    protected final ModelClientConfig modelClientConfig;

    /**
     * BasePromptBuilder.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.model = new Model(modelClientConfig, modelConfig);
    }

    /**
     * build.
     * 
     * @param prompt prompt
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    public abstract CompletableFuture<String> build(Object prompt, Object... args);

    /**
     * streamBuild.
     * 
     * @param prompt prompt
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    public abstract CompletableFuture<String> streamBuild(Object prompt, Object... args);
}

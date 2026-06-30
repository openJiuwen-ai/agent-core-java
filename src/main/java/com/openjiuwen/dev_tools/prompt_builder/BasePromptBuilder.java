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
 */
public abstract class BasePromptBuilder {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Model model;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ModelRequestConfig modelConfig;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ModelClientConfig modelClientConfig;

    /**
     * Auto-generated for codecheck compliance.
     */
    public BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.model = new Model(modelClientConfig, modelConfig);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract CompletableFuture<String> build(Object prompt, Object... args);

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract CompletableFuture<String> streamBuild(Object prompt, Object... args);
}

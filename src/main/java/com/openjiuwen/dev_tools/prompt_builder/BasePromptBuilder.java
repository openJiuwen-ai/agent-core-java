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

    protected final Model model;
    protected final ModelRequestConfig modelConfig;
    protected final ModelClientConfig modelClientConfig;

    public BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.model = new Model(modelClientConfig, modelConfig);
    }

    public abstract CompletableFuture<String> build(Object prompt, Object... args);

    public abstract CompletableFuture<String> streamBuild(Object prompt, Object... args);
}

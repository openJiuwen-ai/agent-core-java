/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Abstract base for prompt builders backed by an LLM model.
 *
 * <p>Mirrors Python's {@code BasePromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/base.py}.</p>
 */
public abstract class BasePromptBuilder {
    protected final Model model;

    protected BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.model = new Model(modelClientConfig, modelConfig);
    }

    public Model getModel() {
        return model;
    }

    public CompletableFuture<Optional<String>> build() {
        return build(List.of(), Map.of());
    }

    public abstract CompletableFuture<Optional<String>> build(
            List<Object> args,
            Map<String, Object> kwargs);

    public Flow.Publisher<?> streamBuild() {
        return streamBuild(List.of(), Map.of());
    }

    public abstract Flow.Publisher<?> streamBuild(
            List<Object> args,
            Map<String, Object> kwargs);
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Abstract base class for all prompt builders.
 *
 * <p>Mirrors Python's {@code BasePromptBuilder}.</p>
 */
public abstract class BasePromptBuilder {

    protected final Model model;
    protected final ModelRequestConfig modelConfig;
    protected final ModelClientConfig modelClientConfig;

    protected BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.model = new Model(modelClientConfig, modelConfig);
    }

    /**
     * Build an improved prompt based on the given cases.
     *
     * @param prompt the original prompt string
     * @param cases  list of evaluated cases
     * @return Mono emitting the improved prompt string
     */
    public abstract Mono<String> build(String prompt, List<EvaluatedCase> cases);

    /**
     * Build an improved prompt as a stream.
     *
     * @param prompt the original prompt string
     * @param cases  list of evaluated cases
     * @return Flux emitting chunks of the improved prompt
     */
    public abstract Flux<String> streamBuild(String prompt, List<EvaluatedCase> cases);
}

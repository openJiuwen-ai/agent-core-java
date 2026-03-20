/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.concurrent.CompletableFuture;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 提示词构建器基类
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.base.BasePromptBuilder}
 * <p>
 * 提供抽象的提示词构建接口，子类需要实现 {@link #build} 和 {@link #streamBuild} 方法。
 * Abstract base class for all prompt builders.
 *
 * <p>Mirrors Python's {@code BasePromptBuilder}.</p>
 */
public abstract class BasePromptBuilder {

    /**
     * 模型实例
     */
    protected final Model model;
    protected final ModelRequestConfig modelConfig;
    protected final ModelClientConfig modelClientConfig;

    /**
     * 构造函数
     *
     * @param modelConfig       模型请求配置
     * @param modelClientConfig 模型客户端配置
     */
    public BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
    protected BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.model = new Model(modelClientConfig, modelConfig);
    }

    /**
     * 构建提示词
     * Build an improved prompt based on the given cases.
     *
     * @param prompt 提示词内容（String 或 PromptTemplate）
     * @param args   额外参数
     * @return 构建后的提示词内容
     * @param prompt the original prompt string
     * @param cases  list of evaluated cases
     * @return Mono emitting the improved prompt string
     */
    public abstract CompletableFuture<String> build(Object prompt, Object... args);
    public abstract Mono<String> build(String prompt, List<EvaluatedCase> cases);

    /**
     * 流式构建提示词
     * Build an improved prompt as a stream.
     *
     * @param prompt 提示词内容（String 或 PromptTemplate）
     * @param args   额外参数
     * @return 流式返回的提示词内容
     * @param prompt the original prompt string
     * @param cases  list of evaluated cases
     * @return Flux emitting chunks of the improved prompt
     */
    public abstract CompletableFuture<String> streamBuild(Object prompt, Object... args);
    public abstract Flux<String> streamBuild(String prompt, List<EvaluatedCase> cases);
}

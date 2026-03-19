// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.concurrent.CompletableFuture;

/**
 * 提示词构建器基类
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.base.BasePromptBuilder}
 * <p>
 * 提供抽象的提示词构建接口，子类需要实现 {@link #build} 和 {@link #streamBuild} 方法。
 */
public abstract class BasePromptBuilder {

    /**
     * 模型实例
     */
    protected final Model model;

    /**
     * 构造函数
     *
     * @param modelConfig       模型请求配置
     * @param modelClientConfig 模型客户端配置
     */
    public BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.model = new Model(modelClientConfig, modelConfig);
    }

    /**
     * 构建提示词
     *
     * @param prompt 提示词内容（String 或 PromptTemplate）
     * @param args   额外参数
     * @return 构建后的提示词内容
     */
    public abstract CompletableFuture<String> build(Object prompt, Object... args);

    /**
     * 流式构建提示词
     *
     * @param prompt 提示词内容（String 或 PromptTemplate）
     * @param args   额外参数
     * @return 流式返回的提示词内容
     */
    public abstract CompletableFuture<String> streamBuild(Object prompt, Object... args);
}
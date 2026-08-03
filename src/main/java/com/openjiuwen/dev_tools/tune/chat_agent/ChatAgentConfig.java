/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;

/**
 * Chat agent configuration.
 *
 * <p>Mirrors Python's {@code ChatAgentConfig} in
 * {@code openjiuwen/dev_tools/tune/chat_agent/chat_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatAgentConfig extends AgentConfig {
    private LLMCallConfig llmCallConfig;

    public ChatAgentConfig() {
    }

    public ChatAgentConfig(LLMCallConfig llmCallConfig) {
        this.llmCallConfig = llmCallConfig;
    }

    /**
     * Hides the inherited {@code ModelConfig} property because Python narrows
     * {@code model} to {@code LLMCallConfig} in this subclass.
     */
    @Override
    @JsonIgnore
    public ModelConfig getModel() {
        return super.getModel();
    }

    @Override
    @JsonIgnore
    public void setModel(ModelConfig model) {
        super.setModel(model);
    }

    @JsonProperty("model")
    public LLMCallConfig getLlmCallConfig() {
        return llmCallConfig;
    }

    @JsonProperty("model")
    public void setLlmCallConfig(LLMCallConfig llmCallConfig) {
        this.llmCallConfig = llmCallConfig;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * ChatAgent配置类
 * <p>
 * Mirrors Python's {@code ChatAgentConfig} from {@code dev_tools/tune/chat_agent/chat_config.py}.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatAgentConfig extends AgentConfig {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private LLMCallConfig model;

    /**
     * getLlmCallConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LLMCallConfig getLlmCallConfig() {
        return model;
    }

    /**
     * setLlmCallConfig.
     * 
     * @param model model
     * @since 0.1.7
     */
    public void setLlmCallConfig(LLMCallConfig model) {
        this.model = model;
    }
}

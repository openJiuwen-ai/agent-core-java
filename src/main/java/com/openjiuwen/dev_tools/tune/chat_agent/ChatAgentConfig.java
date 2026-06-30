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
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatAgentConfig extends AgentConfig {

    /**
     * LLM调用配置
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private LLMCallConfig model;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCallConfig getLlmCallConfig() {
        return model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLlmCallConfig(LLMCallConfig model) {
        this.model = model;
    }
}

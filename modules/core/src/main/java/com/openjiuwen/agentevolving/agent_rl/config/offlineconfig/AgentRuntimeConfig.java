/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.AgentRuntimeConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRuntimeConfig {
    private Object systemPrompt = "You are a helpful assistant.";
    private double temperature = 0.7;
    private double topP = 0.9;
    private int maxNewTokens = 512;
    private double presencePenalty = 0.0;
    private double frequencyPenalty = 0.0;

    /**
     * getSystem_prompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getSystem_prompt() {
        return getSystemPrompt();
    }

    /**
     * getTop_p.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTop_p() {
        return getTopP();
    }

    /**
     * getMax_new_tokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMax_new_tokens() {
        return getMaxNewTokens();
    }
}

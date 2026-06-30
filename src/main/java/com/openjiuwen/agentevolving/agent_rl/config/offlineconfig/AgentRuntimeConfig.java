/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.AgentRuntimeConfig.
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
     * Auto-generated for codecheck compliance.
     */
    public Object getSystem_prompt() { return getSystemPrompt(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTop_p() { return getTopP(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMax_new_tokens() { return getMaxNewTokens(); }
}

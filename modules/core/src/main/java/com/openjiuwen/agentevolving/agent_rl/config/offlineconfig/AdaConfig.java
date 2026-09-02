/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.AdaConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdaConfig {
    private int rolloutMaxRound = 2;
    private int finalKeepPerPrompt = 8;

    /**
     * getRollout_max_round.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRollout_max_round() {
        return getRolloutMaxRound();
    }

    /**
     * getFinal_keep_per_prompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getFinal_keep_per_prompt() {
        return getFinalKeepPerPrompt();
    }
}

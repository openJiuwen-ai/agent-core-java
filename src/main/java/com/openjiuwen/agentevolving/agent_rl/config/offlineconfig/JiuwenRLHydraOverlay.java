/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.JiuwenRLHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiuwenRLHydraOverlay {
    private boolean isWholeTrajectory = false;
    private Integer finalKeepPerPrompt;

    /**
     * JiuwenRLHydraCustomFn.
     * 
     * @since 0.1.7
     */
    private JiuwenRLHydraCustomFn customFn = new JiuwenRLHydraCustomFn();

    /**
     * isWhole_trajectory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isWhole_trajectory() {
        return isWholeTrajectory();
    }

    /**
     * getFinal_keep_per_prompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getFinal_keep_per_prompt() {
        return getFinalKeepPerPrompt();
    }

    /**
     * getCustom_fn.
     * 
     * @return the result
     * @since 0.1.7
     */
    public JiuwenRLHydraCustomFn getCustom_fn() {
        return getCustomFn();
    }
}

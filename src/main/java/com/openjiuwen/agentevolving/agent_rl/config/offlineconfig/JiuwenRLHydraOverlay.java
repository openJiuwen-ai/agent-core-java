/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.JiuwenRLHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiuwenRLHydraOverlay {
    private boolean isWholeTrajectory = false;
    private Integer finalKeepPerPrompt;
    private JiuwenRLHydraCustomFn customFn = new JiuwenRLHydraCustomFn();

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isWhole_trajectory() { return isWholeTrajectory(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getFinal_keep_per_prompt() { return getFinalKeepPerPrompt(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public JiuwenRLHydraCustomFn getCustom_fn() { return getCustomFn(); }
}

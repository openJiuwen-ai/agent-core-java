/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlModelHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlModelHydraOverlay {
    private boolean isUseRemovePadding = false;
    private boolean isEnableGradientCheckpointing = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isUse_remove_padding() { return isUseRemovePadding(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnable_gradient_checkpointing() { return isEnableGradientCheckpointing(); }
}

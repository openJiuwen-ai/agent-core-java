/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlModelHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlModelHydraOverlay {
    private boolean isUseRemovePadding = false;
    private boolean isEnableGradientCheckpointing = true;

    /**
     * isUse_remove_padding.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isUse_remove_padding() {
        return isUseRemovePadding();
    }

    /**
     * isEnable_gradient_checkpointing.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnable_gradient_checkpointing() {
        return isEnableGradientCheckpointing();
    }
}

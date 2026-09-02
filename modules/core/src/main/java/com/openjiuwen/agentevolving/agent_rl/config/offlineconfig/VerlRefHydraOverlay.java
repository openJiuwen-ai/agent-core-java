/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlRefHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlRefHydraOverlay {
    private VerlRefFsdpHydraOverlay fsdpConfig = new VerlRefFsdpHydraOverlay();

    /**
     * getFsdp_config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VerlRefFsdpHydraOverlay getFsdp_config() {
        return getFsdpConfig();
    }
}

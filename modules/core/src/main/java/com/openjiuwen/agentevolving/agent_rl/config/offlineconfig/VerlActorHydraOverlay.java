/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlActorHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlActorHydraOverlay {
    private int ppoMiniBatchSize = 16;

    /**
     * VerlActorFsdpHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlActorFsdpHydraOverlay fsdpConfig = new VerlActorFsdpHydraOverlay();

    /**
     * getPpo_mini_batch_size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getPpo_mini_batch_size() {
        return getPpoMiniBatchSize();
    }

    /**
     * getFsdp_config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VerlActorFsdpHydraOverlay getFsdp_config() {
        return getFsdpConfig();
    }
}

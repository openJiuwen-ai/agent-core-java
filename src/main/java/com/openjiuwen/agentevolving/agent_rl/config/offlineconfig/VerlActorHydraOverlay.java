/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlActorHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlActorHydraOverlay {
    private int ppoMiniBatchSize = 16;
    private VerlActorFsdpHydraOverlay fsdpConfig = new VerlActorFsdpHydraOverlay();

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getPpo_mini_batch_size() { return getPpoMiniBatchSize(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public VerlActorFsdpHydraOverlay getFsdp_config() { return getFsdpConfig(); }
}

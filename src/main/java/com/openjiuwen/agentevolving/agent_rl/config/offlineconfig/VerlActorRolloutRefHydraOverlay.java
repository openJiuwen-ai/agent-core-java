/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlActorRolloutRefHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlActorRolloutRefHydraOverlay {
    private VerlModelHydraOverlay model = new VerlModelHydraOverlay();

    /**
     * VerlActorHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlActorHydraOverlay actor = new VerlActorHydraOverlay();

    /**
     * VerlRefHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlRefHydraOverlay ref = new VerlRefHydraOverlay();

    /**
     * VerlRolloutHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlRolloutHydraOverlay rollout = new VerlRolloutHydraOverlay();
}

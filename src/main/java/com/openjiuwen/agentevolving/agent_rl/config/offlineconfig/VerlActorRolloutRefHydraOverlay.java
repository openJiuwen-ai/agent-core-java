/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlActorRolloutRefHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlActorRolloutRefHydraOverlay {
    private VerlModelHydraOverlay model = new VerlModelHydraOverlay();
    private VerlActorHydraOverlay actor = new VerlActorHydraOverlay();
    private VerlRefHydraOverlay ref = new VerlRefHydraOverlay();
    private VerlRolloutHydraOverlay rollout = new VerlRolloutHydraOverlay();
}

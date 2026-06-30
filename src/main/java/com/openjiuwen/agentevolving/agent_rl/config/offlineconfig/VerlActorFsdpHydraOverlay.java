/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlActorFsdpHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlActorFsdpHydraOverlay {
    private boolean isParamOffload = true;
    private boolean isOptimizerOffload = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isParam_offload() { return isParamOffload(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isOptimizer_offload() { return isOptimizerOffload(); }
}

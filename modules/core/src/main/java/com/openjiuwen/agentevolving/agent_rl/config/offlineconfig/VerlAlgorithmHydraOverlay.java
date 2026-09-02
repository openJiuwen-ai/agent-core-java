/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlAlgorithmHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlAlgorithmHydraOverlay {
    private boolean isFilterGroups = false;

    /**
     * isFilter_groups.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isFilter_groups() {
        return isFilterGroups();
    }
}

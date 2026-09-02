/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlTrainerHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlTrainerHydraOverlay {
    private String device = "npu";
    private Integer runtimeParallelNum;
    private Integer rolloutMaxRound;

    /**
     * getRuntime_parallel_num.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getRuntime_parallel_num() {
        return getRuntimeParallelNum();
    }

    /**
     * getRollout_max_round.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getRollout_max_round() {
        return getRolloutMaxRound();
    }
}

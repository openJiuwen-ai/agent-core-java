/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlTrainerHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlTrainerHydraOverlay {
    private String device = "npu";
    private Integer runtimeParallelNum;
    private Integer rolloutMaxRound;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getRuntime_parallel_num() { return getRuntimeParallelNum(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getRollout_max_round() { return getRolloutMaxRound(); }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlRolloutHydraOverlay.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlRolloutHydraOverlay {
    private String mode = "async";
    private String name = "vllm";
    private int tensorModelParallelSize = 1;
    private boolean isEnforceEager = true;
    private double gpuMemoryUtilization = 0.7;
    private boolean isEnableChunkedPrefill = false;

    /**
     * VerlRolloutMultiTurnHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlRolloutMultiTurnHydraOverlay multiTurn = new VerlRolloutMultiTurnHydraOverlay();

    /**
     * VerlEngineKwargsHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlEngineKwargsHydraOverlay engineKwargs = new VerlEngineKwargsHydraOverlay();

    /**
     * getGpu_memory_utilization.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getGpu_memory_utilization() {
        return getGpuMemoryUtilization();
    }

    /**
     * isEnable_chunked_prefill.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnable_chunked_prefill() {
        return isEnableChunkedPrefill();
    }

    /**
     * getMulti_turn.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VerlRolloutMultiTurnHydraOverlay getMulti_turn() {
        return getMultiTurn();
    }

    /**
     * getEngine_kwargs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VerlEngineKwargsHydraOverlay getEngine_kwargs() {
        return getEngineKwargs();
    }
}

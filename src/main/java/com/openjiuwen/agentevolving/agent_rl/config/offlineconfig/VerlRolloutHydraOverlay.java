/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlRolloutHydraOverlay.
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
    private VerlRolloutMultiTurnHydraOverlay multiTurn = new VerlRolloutMultiTurnHydraOverlay();
    private VerlEngineKwargsHydraOverlay engineKwargs = new VerlEngineKwargsHydraOverlay();

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getGpu_memory_utilization() { return getGpuMemoryUtilization(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnable_chunked_prefill() { return isEnableChunkedPrefill(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public VerlRolloutMultiTurnHydraOverlay getMulti_turn() { return getMultiTurn(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public VerlEngineKwargsHydraOverlay getEngine_kwargs() { return getEngineKwargs(); }
}

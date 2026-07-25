/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Rollout Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlRolloutHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlRolloutHydraOverlay {

    private String mode = "async";
    private String name = "vllm";
    private int tensorModelParallelSize = 1;
    private boolean enforceEager = true;
    private double gpuMemoryUtilization = 0.7;
    private boolean enableChunkedPrefill = false;
    private VerlRolloutMultiTurnHydraOverlay multiTurn = new VerlRolloutMultiTurnHydraOverlay();
    private VerlEngineKwargsHydraOverlay engineKwargs = new VerlEngineKwargsHydraOverlay();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTensorModelParallelSize() { return tensorModelParallelSize; }
    public void setTensorModelParallelSize(int tensorModelParallelSize) { this.tensorModelParallelSize = tensorModelParallelSize; }
    public boolean isEnforceEager() { return enforceEager; }
    public void setEnforceEager(boolean enforceEager) { this.enforceEager = enforceEager; }
    public double getGpuMemoryUtilization() { return gpuMemoryUtilization; }
    public void setGpuMemoryUtilization(double gpuMemoryUtilization) { this.gpuMemoryUtilization = gpuMemoryUtilization; }
    public boolean isEnableChunkedPrefill() { return enableChunkedPrefill; }
    public void setEnableChunkedPrefill(boolean enableChunkedPrefill) { this.enableChunkedPrefill = enableChunkedPrefill; }
    public VerlRolloutMultiTurnHydraOverlay getMultiTurn() { return multiTurn; }
    public void setMultiTurn(VerlRolloutMultiTurnHydraOverlay multiTurn) { 
        this.multiTurn = multiTurn != null ? multiTurn : new VerlRolloutMultiTurnHydraOverlay(); 
    }
    public VerlEngineKwargsHydraOverlay getEngineKwargs() { return engineKwargs; }
    public void setEngineKwargs(VerlEngineKwargsHydraOverlay engineKwargs) { 
        this.engineKwargs = engineKwargs != null ? engineKwargs : new VerlEngineKwargsHydraOverlay(); 
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Actor Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlActorHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlActorHydraOverlay {

    private int ppoMiniBatchSize = 16;
    private VerlActorFsdpHydraOverlay fsdpConfig = new VerlActorFsdpHydraOverlay();

    public int getPpoMiniBatchSize() { return ppoMiniBatchSize; }
    public void setPpoMiniBatchSize(int ppoMiniBatchSize) { this.ppoMiniBatchSize = ppoMiniBatchSize; }
    public VerlActorFsdpHydraOverlay getFsdpConfig() { return fsdpConfig; }
    public void setFsdpConfig(VerlActorFsdpHydraOverlay fsdpConfig) { 
        this.fsdpConfig = fsdpConfig != null ? fsdpConfig : new VerlActorFsdpHydraOverlay(); 
    }
}

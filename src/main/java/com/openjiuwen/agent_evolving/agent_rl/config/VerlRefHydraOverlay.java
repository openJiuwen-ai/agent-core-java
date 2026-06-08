/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Reference Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlRefHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlRefHydraOverlay {

    private VerlRefFsdpHydraOverlay fsdpConfig = new VerlRefFsdpHydraOverlay();

    public VerlRefFsdpHydraOverlay getFsdpConfig() { return fsdpConfig; }
    public void setFsdpConfig(VerlRefFsdpHydraOverlay fsdpConfig) { 
        this.fsdpConfig = fsdpConfig != null ? fsdpConfig : new VerlRefFsdpHydraOverlay(); 
    }
}

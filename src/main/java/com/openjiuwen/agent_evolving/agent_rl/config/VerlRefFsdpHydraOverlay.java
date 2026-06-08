/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Reference FSDP Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlRefFsdpHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlRefFsdpHydraOverlay {

    private boolean paramOffload = true;

    public boolean isParamOffload() { return paramOffload; }
    public void setParamOffload(boolean paramOffload) { this.paramOffload = paramOffload; }
}

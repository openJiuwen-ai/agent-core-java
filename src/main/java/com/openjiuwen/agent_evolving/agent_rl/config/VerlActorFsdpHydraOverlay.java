/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Actor FSDP Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlActorFsdpHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlActorFsdpHydraOverlay {

    private boolean paramOffload = true;
    private boolean optimizerOffload = true;

    public boolean isParamOffload() { return paramOffload; }
    public void setParamOffload(boolean paramOffload) { this.paramOffload = paramOffload; }
    public boolean isOptimizerOffload() { return optimizerOffload; }
    public void setOptimizerOffload(boolean optimizerOffload) { this.optimizerOffload = optimizerOffload; }
}

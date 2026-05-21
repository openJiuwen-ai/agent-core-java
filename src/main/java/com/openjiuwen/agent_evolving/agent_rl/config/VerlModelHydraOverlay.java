/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Model Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlModelHydraOverlay} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class VerlModelHydraOverlay {

    private boolean useRemovePadding = false;
    private boolean enableGradientCheckpointing = true;

    public boolean isUseRemovePadding() { return useRemovePadding; }
    public void setUseRemovePadding(boolean useRemovePadding) { this.useRemovePadding = useRemovePadding; }
    public boolean isEnableGradientCheckpointing() { return enableGradientCheckpointing; }
    public void setEnableGradientCheckpointing(boolean enableGradientCheckpointing) { this.enableGradientCheckpointing = enableGradientCheckpointing; }
}
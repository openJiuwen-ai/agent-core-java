/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Data Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlDataHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlDataHydraOverlay {

    private boolean filterOverlongPrompts = false;

    public boolean isFilterOverlongPrompts() { return filterOverlongPrompts; }
    public void setFilterOverlongPrompts(boolean filterOverlongPrompts) { this.filterOverlongPrompts = filterOverlongPrompts; }
}

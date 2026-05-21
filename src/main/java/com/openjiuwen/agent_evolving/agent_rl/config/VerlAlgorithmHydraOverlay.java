/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Algorithm Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlAlgorithmHydraOverlay} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class VerlAlgorithmHydraOverlay {

    private boolean filterGroups = false;

    public boolean isFilterGroups() { return filterGroups; }
    public void setFilterGroups(boolean filterGroups) { this.filterGroups = filterGroups; }
}
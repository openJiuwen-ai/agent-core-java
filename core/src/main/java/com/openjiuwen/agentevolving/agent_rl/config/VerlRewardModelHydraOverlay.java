/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Reward Model Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlRewardModelHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlRewardModelHydraOverlay {

    private String rewardManager = "naive";

    public String getRewardManager() { return rewardManager; }
    public void setRewardManager(String rewardManager) { this.rewardManager = rewardManager; }
}

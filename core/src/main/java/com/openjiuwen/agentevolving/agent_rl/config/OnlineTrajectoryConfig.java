/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Trajectory configuration for online RL.
 * <p>
 * Mirrors Python's {@code TrajectoryConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/online_config.py}.
 */
public class OnlineTrajectoryConfig {

    private int batchSize = 4;
    private String mode = "feedback_level";

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { 
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        this.batchSize = batchSize; 
    }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}

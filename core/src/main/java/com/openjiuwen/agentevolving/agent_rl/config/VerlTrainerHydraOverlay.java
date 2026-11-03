/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Trainer Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlTrainerHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlTrainerHydraOverlay {

    private String device = "npu";
    private Integer runtimeParallelNum = null;
    private Integer rolloutMaxRound = null;

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public Integer getRuntimeParallelNum() { return runtimeParallelNum; }
    public void setRuntimeParallelNum(Integer runtimeParallelNum) { this.runtimeParallelNum = runtimeParallelNum; }
    public Integer getRolloutMaxRound() { return rolloutMaxRound; }
    public void setRolloutMaxRound(Integer rolloutMaxRound) { this.rolloutMaxRound = rolloutMaxRound; }
}

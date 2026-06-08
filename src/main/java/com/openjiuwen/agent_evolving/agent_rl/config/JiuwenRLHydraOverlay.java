/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Jiuwen RL Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code JiuwenRLHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class JiuwenRLHydraOverlay {

    private boolean wholeTrajectory = false;
    private Integer finalKeepPerPrompt = null;
    private JiuwenRLHydraCustomFn customFn = new JiuwenRLHydraCustomFn();

    public boolean isWholeTrajectory() { return wholeTrajectory; }
    public void setWholeTrajectory(boolean wholeTrajectory) { this.wholeTrajectory = wholeTrajectory; }
    public Integer getFinalKeepPerPrompt() { return finalKeepPerPrompt; }
    public void setFinalKeepPerPrompt(Integer finalKeepPerPrompt) { this.finalKeepPerPrompt = finalKeepPerPrompt; }
    public JiuwenRLHydraCustomFn getCustomFn() { return customFn; }
    public void setCustomFn(JiuwenRLHydraCustomFn customFn) { 
        this.customFn = customFn != null ? customFn : new JiuwenRLHydraCustomFn(); 
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Parameters for the Ada rollout variant.
 * <p>
 * When {@code RLConfig.ada} is set, Ada is enabled: {@code trainer.rollout_max_round}
 * is taken from {@code rolloutMaxRound} below, and the custom classifier / validator /
 * sampler are wired in.
 * <p>
 * Mirrors Python's {@code AdaConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class AdaConfig {

    private int rolloutMaxRound = 2;
    private int finalKeepPerPrompt = 8;

    public int getRolloutMaxRound() { return rolloutMaxRound; }
    public void setRolloutMaxRound(int rolloutMaxRound) { this.rolloutMaxRound = rolloutMaxRound; }
    public int getFinalKeepPerPrompt() { return finalKeepPerPrompt; }
    public void setFinalKeepPerPrompt(int finalKeepPerPrompt) { this.finalKeepPerPrompt = finalKeepPerPrompt; }
}

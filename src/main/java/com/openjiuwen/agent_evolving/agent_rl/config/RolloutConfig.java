/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Rollout / actor optimizer configuration.
 * <p>
 * Mirrors Python's {@code RolloutConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class RolloutConfig {

    private double actorOptimizerLr = 1e-6;
    private boolean actorUseKlLoss = false;
    private double actorKlLossCoef = 0.02;
    private double actorEntropyCoef = 0.0;
    private double actorClipRatioLow = 0.2;
    private double actorClipRatioHigh = 0.3;
    private String actorLossAggMode = "seq-mean-token-mean";
    private int rolloutN = 8;

    public double getActorOptimizerLr() { return actorOptimizerLr; }
    public void setActorOptimizerLr(double actorOptimizerLr) { this.actorOptimizerLr = actorOptimizerLr; }
    public boolean isActorUseKlLoss() { return actorUseKlLoss; }
    public void setActorUseKlLoss(boolean actorUseKlLoss) { this.actorUseKlLoss = actorUseKlLoss; }
    public double getActorKlLossCoef() { return actorKlLossCoef; }
    public void setActorKlLossCoef(double actorKlLossCoef) { this.actorKlLossCoef = actorKlLossCoef; }
    public double getActorEntropyCoef() { return actorEntropyCoef; }
    public void setActorEntropyCoef(double actorEntropyCoef) { this.actorEntropyCoef = actorEntropyCoef; }
    public double getActorClipRatioLow() { return actorClipRatioLow; }
    public void setActorClipRatioLow(double actorClipRatioLow) { this.actorClipRatioLow = actorClipRatioLow; }
    public double getActorClipRatioHigh() { return actorClipRatioHigh; }
    public void setActorClipRatioHigh(double actorClipRatioHigh) { this.actorClipRatioHigh = actorClipRatioHigh; }
    public String getActorLossAggMode() { return actorLossAggMode; }
    public void setActorLossAggMode(String actorLossAggMode) { this.actorLossAggMode = actorLossAggMode; }
    public int getRolloutN() { return rolloutN; }
    public void setRolloutN(int rolloutN) { this.rolloutN = rolloutN; }
}
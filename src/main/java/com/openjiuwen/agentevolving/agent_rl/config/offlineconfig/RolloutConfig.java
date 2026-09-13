/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.RolloutConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RolloutConfig {
    private double actorOptimizerLr = 1e-6;
    private boolean isActorUseKlLoss = false;
    private double actorKlLossCoef = 0.02;
    private double actorEntropyCoef = 0.0;
    private double actorClipRatioLow = 0.2;
    private double actorClipRatioHigh = 0.3;
    private String actorLossAggMode = "seq-mean-token-mean";
    private int rolloutN = 8;

    /**
     * getActor_optimizer_lr.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getActor_optimizer_lr() {
        return getActorOptimizerLr();
    }

    /**
     * isActor_use_kl_loss.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isActor_use_kl_loss() {
        return isActorUseKlLoss();
    }

    /**
     * getActor_kl_loss_coef.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getActor_kl_loss_coef() {
        return getActorKlLossCoef();
    }

    /**
     * getActor_clip_ratio_low.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getActor_clip_ratio_low() {
        return getActorClipRatioLow();
    }

    /**
     * getActor_clip_ratio_high.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getActor_clip_ratio_high() {
        return getActorClipRatioHigh();
    }

    /**
     * getActor_loss_agg_mode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getActor_loss_agg_mode() {
        return getActorLossAggMode();
    }

    /**
     * getRollout_n.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRollout_n() {
        return getRolloutN();
    }
}

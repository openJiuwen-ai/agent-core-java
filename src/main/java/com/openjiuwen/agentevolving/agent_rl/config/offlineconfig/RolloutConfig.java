/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.RolloutConfig.
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
     * Auto-generated for codecheck compliance.
     */
    public double getActor_optimizer_lr() { return getActorOptimizerLr(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isActor_use_kl_loss() { return isActorUseKlLoss(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getActor_kl_loss_coef() { return getActorKlLossCoef(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getActor_clip_ratio_low() { return getActorClipRatioLow(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getActor_clip_ratio_high() { return getActorClipRatioHigh(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getActor_loss_agg_mode() { return getActorLossAggMode(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRollout_n() { return getRolloutN(); }
}

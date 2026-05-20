/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlHydraOverlay.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlHydraOverlay {
    private VerlDataHydraOverlay data = new VerlDataHydraOverlay();
    private VerlAlgorithmHydraOverlay algorithm = new VerlAlgorithmHydraOverlay();
    @JsonProperty("actor_rollout_ref")
    private VerlActorRolloutRefHydraOverlay actorRolloutRef = new VerlActorRolloutRefHydraOverlay();
    private VerlTrainerHydraOverlay trainer = new VerlTrainerHydraOverlay();
    @JsonProperty("reward_model")
    private VerlRewardModelHydraOverlay rewardModel = new VerlRewardModelHydraOverlay();

    private JiuwenRLHydraOverlay jiuwenRL = new JiuwenRLHydraOverlay();

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonProperty("JiuwenRL")
    /**
     * Auto-generated for codecheck compliance.
     */
    public JiuwenRLHydraOverlay getJiuwenRL() {
        return jiuwenRL;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonProperty("JiuwenRL")
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setJiuwenRL(JiuwenRLHydraOverlay jiuwenRL) {
        this.jiuwenRL = jiuwenRL;
    }

    /** Auto-generated for codecheck compliance. */
    @JsonIgnore
    public VerlActorRolloutRefHydraOverlay getActor_rollout_ref() {
        return getActorRolloutRef();
    }

    /** Auto-generated for codecheck compliance. */
    @JsonIgnore
    public void setActor_rollout_ref(VerlActorRolloutRefHydraOverlay value) {
        setActorRolloutRef(value);
    }

    /** Auto-generated for codecheck compliance. */
    @JsonIgnore
    public VerlRewardModelHydraOverlay getReward_model() {
        return getRewardModel();
    }

    /** Auto-generated for codecheck compliance. */
    @JsonIgnore
    public void setReward_model(VerlRewardModelHydraOverlay value) {
        setRewardModel(value);
    }
}

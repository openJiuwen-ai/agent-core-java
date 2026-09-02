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
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlHydraOverlay {
    private VerlDataHydraOverlay data = new VerlDataHydraOverlay();

    /**
     * VerlAlgorithmHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlAlgorithmHydraOverlay algorithm = new VerlAlgorithmHydraOverlay();
    @JsonProperty("actor_rollout_ref")
    /**
     * VerlActorRolloutRefHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlActorRolloutRefHydraOverlay actorRolloutRef = new VerlActorRolloutRefHydraOverlay();

    /**
     * VerlTrainerHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlTrainerHydraOverlay trainer = new VerlTrainerHydraOverlay();
    @JsonProperty("reward_model")
    /**
     * VerlRewardModelHydraOverlay.
     * 
     * @since 0.1.7
     */
    private VerlRewardModelHydraOverlay rewardModel = new VerlRewardModelHydraOverlay();

    /**
     * JiuwenRLHydraOverlay.
     * 
     * @since 0.1.7
     */
    private JiuwenRLHydraOverlay jiuwenRL = new JiuwenRLHydraOverlay();

    /**
     * getJiuwenRL.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonProperty("JiuwenRL")
    public JiuwenRLHydraOverlay getJiuwenRL() {
        return jiuwenRL;
    }

    /**
     * setJiuwenRL.
     * 
     * @param jiuwenRL jiuwenRL
     * @since 0.1.7
     */
    @JsonProperty("JiuwenRL")
    public void setJiuwenRL(JiuwenRLHydraOverlay jiuwenRL) {
        this.jiuwenRL = jiuwenRL;
    }

    /**
     * getActor_rollout_ref.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public VerlActorRolloutRefHydraOverlay getActor_rollout_ref() {
        return getActorRolloutRef();
    }

    /**
     * setActor_rollout_ref.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setActor_rollout_ref(VerlActorRolloutRefHydraOverlay value) {
        setActorRolloutRef(value);
    }

    /**
     * getReward_model.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public VerlRewardModelHydraOverlay getReward_model() {
        return getRewardModel();
    }

    /**
     * setReward_model.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setReward_model(VerlRewardModelHydraOverlay value) {
        setRewardModel(value);
    }
}

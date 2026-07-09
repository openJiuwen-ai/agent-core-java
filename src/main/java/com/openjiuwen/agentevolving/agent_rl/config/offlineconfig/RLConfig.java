/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.RLConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RLConfig {
    private TrainingConfig training;

    /**
     * RolloutConfig.
     * 
     * @since 0.1.7
     */
    private RolloutConfig rollout = new RolloutConfig();

    /**
     * AgentRuntimeConfig.
     * 
     * @since 0.1.7
     */
    private AgentRuntimeConfig runtime = new AgentRuntimeConfig();

    /**
     * PersistenceConfig.
     * 
     * @since 0.1.7
     */
    private PersistenceConfig persistence = new PersistenceConfig();
    private AdaConfig ada;

    /**
     * RLConfig.
     * 
     * @param training training
     * @since 0.1.7
     */
    public RLConfig(TrainingConfig training) {
        this(training, null, null, null, null);
    }

    /**
     * RLConfig.
     * 
     * @param training training
     * @param rollout rollout
     * @param runtime runtime
     * @param persistence persistence
     * @param ada ada
     * @since 0.1.7
     */
    public RLConfig(TrainingConfig training, RolloutConfig rollout, AgentRuntimeConfig runtime,
            PersistenceConfig persistence, AdaConfig ada) {
        this.training = Objects.requireNonNull(training, "training is required");
        this.rollout = rollout != null ? rollout : new RolloutConfig();
        this.runtime = runtime != null ? runtime : new AgentRuntimeConfig();
        this.persistence = persistence != null ? persistence : new PersistenceConfig();
        this.ada = ada;
    }

    /**
     * setTraining.
     * 
     * @param training training
     * @since 0.1.7
     */
    public void setTraining(TrainingConfig training) {
        this.training = Objects.requireNonNull(training, "training is required");
    }
}

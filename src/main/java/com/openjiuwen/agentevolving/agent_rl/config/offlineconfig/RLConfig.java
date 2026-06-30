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
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RLConfig {
    private TrainingConfig training;
    private RolloutConfig rollout = new RolloutConfig();
    private AgentRuntimeConfig runtime = new AgentRuntimeConfig();
    private PersistenceConfig persistence = new PersistenceConfig();
    private AdaConfig ada;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RLConfig(TrainingConfig training) {
        this(training, null, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RLConfig(
            TrainingConfig training,
            RolloutConfig rollout,
            AgentRuntimeConfig runtime,
            PersistenceConfig persistence,
            AdaConfig ada
    ) {
        this.training = Objects.requireNonNull(training, "training is required");
        this.rollout = rollout != null ? rollout : new RolloutConfig();
        this.runtime = runtime != null ? runtime : new AgentRuntimeConfig();
        this.persistence = persistence != null ? persistence : new PersistenceConfig();
        this.ada = ada;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTraining(TrainingConfig training) {
        this.training = Objects.requireNonNull(training, "training is required");
    }
}

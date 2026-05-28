/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Top level RL configuration.
 * <p>
 * Mirrors Python's {@code RLConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class RLConfig {

    private TrainingConfig training;
    private RolloutConfig rollout = new RolloutConfig();
    private AgentRuntimeConfig runtime = new AgentRuntimeConfig();
    private PersistenceConfig persistence = new PersistenceConfig();
    private AdaConfig ada; // Optional - if provided, enable Ada rollout variant

    public RLConfig() {
    }

    public RLConfig(TrainingConfig training) {
        this.training = training;
    }

    public TrainingConfig getTraining() { return training; }
    public void setTraining(TrainingConfig training) { this.training = training; }
    
    public RolloutConfig getRollout() { return rollout; }
    public void setRollout(RolloutConfig rollout) { 
        this.rollout = rollout != null ? rollout : new RolloutConfig(); 
    }
    
    public AgentRuntimeConfig getRuntime() { return runtime; }
    public void setRuntime(AgentRuntimeConfig runtime) { 
        this.runtime = runtime != null ? runtime : new AgentRuntimeConfig(); 
    }
    
    public PersistenceConfig getPersistence() { return persistence; }
    public void setPersistence(PersistenceConfig persistence) { 
        this.persistence = persistence != null ? persistence : new PersistenceConfig(); 
    }
    
    public AdaConfig getAda() { return ada; }
    public void setAda(AdaConfig ada) { this.ada = ada; }

    public void validate() {
        if (training == null) {
            throw new IllegalArgumentException("training must not be null");
        }
    }
}
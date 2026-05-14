/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Top-level RL configuration.
 * <p>
 * Mirrors Python's {@code RLConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class RLConfig {

    private TrainingConfig training;
    private PersistenceConfig persistence = new PersistenceConfig();

    public RLConfig() {
    }

    public RLConfig(TrainingConfig training) {
        this.training = training;
    }

    public TrainingConfig getTraining() { return training; }
    public void setTraining(TrainingConfig training) { this.training = training; }
    public PersistenceConfig getPersistence() { return persistence; }
    public void setPersistence(PersistenceConfig persistence) { this.persistence = persistence != null ? persistence : new PersistenceConfig(); }

    public void validate() {
        if (training == null) {
            throw new IllegalArgumentException("training must not be null");
        }
    }
}

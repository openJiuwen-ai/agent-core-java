/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Top-level launcher config for online RL.
 * <p>
 * CLI / optional YAML overlays merge on top of defaults.
 * <p>
 * Mirrors Python's {@code OnlineRLConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.online_config}.
 */
public class OnlineRLConfig {

    private boolean demo = false;
    private VLLMServiceConfig inference = new VLLMServiceConfig();
    private JudgeConfig judge = new JudgeConfig();
    private GatewayServiceConfig gateway = new GatewayServiceConfig();
    private OnlineTrajectoryConfig trajectory = new OnlineTrajectoryConfig();
    private OnlineTrainingConfig training = new OnlineTrainingConfig();
    private JiuwenConfig jiuwen = new JiuwenConfig();

    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
    
    public VLLMServiceConfig getInference() { return inference; }
    public void setInference(VLLMServiceConfig inference) { 
        this.inference = inference != null ? inference : new VLLMServiceConfig(); 
    }
    
    public JudgeConfig getJudge() { return judge; }
    public void setJudge(JudgeConfig judge) { 
        this.judge = judge != null ? judge : new JudgeConfig(); 
    }
    
    public GatewayServiceConfig getGateway() { return gateway; }
    public void setGateway(GatewayServiceConfig gateway) { 
        this.gateway = gateway != null ? gateway : new GatewayServiceConfig(); 
    }
    
    public OnlineTrajectoryConfig getTrajectory() { return trajectory; }
    public void setTrajectory(OnlineTrajectoryConfig trajectory) { 
        this.trajectory = trajectory != null ? trajectory : new OnlineTrajectoryConfig(); 
    }
    
    public OnlineTrainingConfig getTraining() { return training; }
    public void setTraining(OnlineTrainingConfig training) { 
        this.training = training != null ? training : new OnlineTrainingConfig(); 
    }
    
    public JiuwenConfig getJiuwen() { return jiuwen; }
    public void setJiuwen(JiuwenConfig jiuwen) { 
        this.jiuwen = jiuwen != null ? jiuwen : new JiuwenConfig(); 
    }

    /**
     * Validates and syncs configuration values.
     * Throws IllegalArgumentException if validation fails.
     */
    public void validate() {
        // Sync judge model with inference if reuse is enabled
        if (judge.isReuseInferenceIfSameModel()) {
            judge.setModelPath(inference.getModelPath());
            judge.setModelName(inference.getModelName());
        }
        
        // Validate inference port
        if (inference.getExistingUrl() == null && inference.getPort() == null) {
            throw new IllegalArgumentException(
                "inference.port is required when inference.existingUrl is not set");
        }
        
        // Validate judge port
        if (judge.getExistingUrl() == null && judge.getPort() == null) {
            throw new IllegalArgumentException(
                "judge.port is required when judge.existingUrl is not set");
        }
        
        // Validate gateway
        if (gateway.getPort() == null) {
            throw new IllegalArgumentException("gateway.port is required");
        }
        if (gateway.getRedisUrl() == null || gateway.getRedisUrl().isEmpty()) {
            throw new IllegalArgumentException("gateway.redisUrl is required");
        }
        
        // Validate jiuwen if enabled
        if (jiuwen.isEnabled()) {
            if (jiuwen.getAgentServerPort() == null) {
                throw new IllegalArgumentException(
                    "jiuwen.agentServerPort is required when jiuwen.enabled is true");
            }
            if (jiuwen.getWsPort() == null) {
                throw new IllegalArgumentException(
                    "jiuwen.wsPort is required when jiuwen.enabled is true");
            }
            if (jiuwen.getWebPort() == null) {
                throw new IllegalArgumentException(
                    "jiuwen.webPort is required when jiuwen.enabled is true");
            }
        }
    }
}
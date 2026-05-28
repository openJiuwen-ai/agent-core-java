/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Runtime hyper-parameters for the agent / inference.
 * <p>
 * Mirrors Python's {@code AgentRuntimeConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class AgentRuntimeConfig {

    private Object systemPrompt = "You are a helpful assistant.";
    private double temperature = 0.7;
    private double topP = 0.9;
    private int maxNewTokens = 512;
    private double presencePenalty = 0.0;
    private double frequencyPenalty = 0.0;

    public Object getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(Object systemPrompt) { this.systemPrompt = systemPrompt; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }
    public int getMaxNewTokens() { return maxNewTokens; }
    public void setMaxNewTokens(int maxNewTokens) { this.maxNewTokens = maxNewTokens; }
    public double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(double presencePenalty) { this.presencePenalty = presencePenalty; }
    public double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
}
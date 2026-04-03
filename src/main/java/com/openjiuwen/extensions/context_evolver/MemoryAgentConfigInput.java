// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.context_evolver;

/**
 * Input parameters for create_memory_agent_config function.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.MemoryAgentConfigInput}.
 */
public class MemoryAgentConfigInput {

    private String modelProvider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private String systemPrompt;
    private int maxIterations = 5;

    public MemoryAgentConfigInput() {
    }

    public MemoryAgentConfigInput(String modelProvider, String apiKey, String apiBase, String modelName) {
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
    }

    public MemoryAgentConfigInput(
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            String systemPrompt) {
        this(modelProvider, apiKey, apiBase, modelName);
        this.systemPrompt = systemPrompt;
    }

    public MemoryAgentConfigInput(
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            String systemPrompt,
            int maxIterations) {
        this(modelProvider, apiKey, apiBase, modelName, systemPrompt);
        this.maxIterations = maxIterations;
    }

    // Getters and Setters
    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}

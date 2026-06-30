/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryAgentConfigInput() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryAgentConfigInput(String modelProvider, String apiKey, String apiBase, String modelName) {
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryAgentConfigInput(
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            String systemPrompt) {
        this(modelProvider, apiKey, apiBase, modelName);
        this.systemPrompt = systemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModelProvider() {
        return modelProvider;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiBase() {
        return apiBase;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}

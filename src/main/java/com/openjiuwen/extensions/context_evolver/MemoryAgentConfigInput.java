/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

/**
 * Input parameters for create_memory_agent_config function.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.MemoryAgentConfigInput}.
 * 
 * @since 0.1.7
 */
public class MemoryAgentConfigInput {
    private String modelProvider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private String systemPrompt;
    private int maxIterations = 5;

    /**
     * MemoryAgentConfigInput.
     * 
     * @since 0.1.7
     */
    public MemoryAgentConfigInput() {
    }

    /**
     * MemoryAgentConfigInput.
     * 
     * @param modelProvider modelProvider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @since 0.1.7
     */
    public MemoryAgentConfigInput(String modelProvider, String apiKey, String apiBase, String modelName) {
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
    }

    /**
     * MemoryAgentConfigInput.
     * 
     * @param modelProvider modelProvider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @param systemPrompt systemPrompt
     * @since 0.1.7
     */
    public MemoryAgentConfigInput(String modelProvider, String apiKey, String apiBase, String modelName,
            String systemPrompt) {
        this(modelProvider, apiKey, apiBase, modelName);
        this.systemPrompt = systemPrompt;
    }

    /**
     * MemoryAgentConfigInput.
     * 
     * @param modelProvider modelProvider
     * @param apiKey apiKey
     * @param apiBase apiBase
     * @param modelName modelName
     * @param systemPrompt systemPrompt
     * @param maxIterations maxIterations
     * @since 0.1.7
     */
    public MemoryAgentConfigInput(String modelProvider, String apiKey, String apiBase, String modelName,
            String systemPrompt, int maxIterations) {
        this(modelProvider, apiKey, apiBase, modelName, systemPrompt);
        this.maxIterations = maxIterations;
    }

    // Getters and Setters
    /**
     * getModelProvider.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModelProvider() {
        return modelProvider;
    }

    /**
     * setModelProvider.
     * 
     * @param modelProvider modelProvider
     * @since 0.1.7
     */
    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * getApiKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * setApiKey.
     * 
     * @param apiKey apiKey
     * @since 0.1.7
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * getApiBase.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApiBase() {
        return apiBase;
    }

    /**
     * setApiBase.
     * 
     * @param apiBase apiBase
     * @since 0.1.7
     */
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    /**
     * getModelName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * setModelName.
     * 
     * @param modelName modelName
     * @since 0.1.7
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * getSystemPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * setSystemPrompt.
     * 
     * @param systemPrompt systemPrompt
     * @since 0.1.7
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * getMaxIterations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * setMaxIterations.
     * 
     * @param maxIterations maxIterations
     * @since 0.1.7
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.judge;

/**
 * Judge evaluator configuration.
 * <p>
 * Mirrors Python's {@code JudgeEvaluatorConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/evaluator.py}.
 */
public class JudgeEvaluatorConfig {

    private String llmUrl;
    private String modelId;
    private String apiKey = "";
    private int numVotes = 1;
    private double temperature = 0.1;
    private int maxCompletionTokens = 4096;
    private int maxRetries;
    private double retryBackoffSec;

    public JudgeEvaluatorConfig(String llmUrl, String modelId) {
        this.llmUrl = llmUrl;
        this.modelId = modelId;
    }

    public JudgeEvaluatorConfig(String llmUrl, String modelId, String apiKey, int numVotes, double temperature,
                                int maxCompletionTokens, int maxRetries, double retryBackoffSec) {
        this.llmUrl = llmUrl;
        this.modelId = modelId;
        this.apiKey = apiKey != null ? apiKey : "";
        this.numVotes = numVotes;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
        this.maxRetries = maxRetries;
        this.retryBackoffSec = retryBackoffSec;
    }

    public String getLlmUrl() {
        return llmUrl;
    }

    public void setLlmUrl(String llmUrl) {
        this.llmUrl = llmUrl;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getNumVotes() {
        return numVotes;
    }

    public void setNumVotes(int numVotes) {
        this.numVotes = numVotes;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public double getRetryBackoffSec() {
        return retryBackoffSec;
    }

    public void setRetryBackoffSec(double retryBackoffSec) {
        this.retryBackoffSec = retryBackoffSec;
    }
}

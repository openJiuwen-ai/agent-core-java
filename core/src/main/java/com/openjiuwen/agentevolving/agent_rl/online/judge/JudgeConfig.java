/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.judge;

/**
 * Judge server runtime configuration.
 * <p>
 * Mirrors Python's {@code JudgeConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/judge_server.py}.
 */
public class JudgeConfig extends JudgeEvaluatorConfig {

    private double timeout = 120.0d;
    private String expectedApiKey = "";

    public JudgeConfig(String llmUrl, String modelId) {
        super(llmUrl, modelId);
    }

    public JudgeConfig(String llmUrl,
                       String modelId,
                       String apiKey,
                       int numVotes,
                       double temperature,
                       int maxCompletionTokens,
                       int maxRetries,
                       double retryBackoffSec,
                       double timeout,
                       String expectedApiKey) {
        super(llmUrl, modelId, apiKey, numVotes, temperature, maxCompletionTokens, maxRetries, retryBackoffSec);
        this.timeout = timeout;
        this.expectedApiKey = expectedApiKey == null ? "" : expectedApiKey;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public String getExpectedApiKey() {
        return expectedApiKey;
    }

    public void setExpectedApiKey(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey == null ? "" : expectedApiKey;
    }
}

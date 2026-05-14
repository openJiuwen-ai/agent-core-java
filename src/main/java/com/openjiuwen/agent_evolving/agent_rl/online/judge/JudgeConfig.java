/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

/**
 * Judge server/client runtime configuration.
 * <p>
 * Mirrors Python's {@code JudgeConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.judge_server}.
 */
public class JudgeConfig extends JudgeEvaluatorConfig {

    private double timeout = 120.0;
    private String expectedApiKey = "";

    public JudgeConfig(String llmUrl, String modelId) {
        super(llmUrl, modelId);
    }

    public double getTimeout() { return timeout; }
    public void setTimeout(double timeout) { this.timeout = timeout; }
    public String getExpectedApiKey() { return expectedApiKey; }
    public void setExpectedApiKey(String expectedApiKey) { this.expectedApiKey = expectedApiKey; }
}

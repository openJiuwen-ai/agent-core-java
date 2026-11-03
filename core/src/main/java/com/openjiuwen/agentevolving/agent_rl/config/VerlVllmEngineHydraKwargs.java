/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl VLLM Engine Hydra kwargs configuration.
 * <p>
 * Mirrors Python's {@code VerlVllmEngineHydraKwargs} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlVllmEngineHydraKwargs {

    private boolean enableAutoToolChoice = true;
    private String toolCallParser = "hermes";
    private String servedModelName = "agentrl";

    public boolean isEnableAutoToolChoice() { return enableAutoToolChoice; }
    public void setEnableAutoToolChoice(boolean enableAutoToolChoice) { this.enableAutoToolChoice = enableAutoToolChoice; }
    public String getToolCallParser() { return toolCallParser; }
    public void setToolCallParser(String toolCallParser) { this.toolCallParser = toolCallParser; }
    public String getServedModelName() { return servedModelName; }
    public void setServedModelName(String servedModelName) { this.servedModelName = servedModelName; }
}

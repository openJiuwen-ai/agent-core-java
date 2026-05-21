/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import java.util.Map;

/**
 * Factory for creating agent instances for RL training.
 * <p>
 * Mirrors Python's {@code agent_factory} module in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.agent_factory}.
 */
public class AgentFactory {

    private Map<String, Object> config;
    private Object backendProxy;

    public AgentFactory(Map<String, Object> config, Object backendProxy) {
        this.config = config;
        this.backendProxy = backendProxy;
    }

    /**
     * Create an agent for rollout generation.
     * 
     * @param agentType Type of agent to create
     * @return Agent instance
     */
    public Object createAgent(String agentType) {
        // TODO: Implement actual agent creation when agent types are defined
        return null;
    }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public Object getBackendProxy() { return backendProxy; }
    public void setBackendProxy(Object backendProxy) { this.backendProxy = backendProxy; }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Jiuwen configuration for online RL.
 * <p>
 * Mirrors Python's {@code JiuwenConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/online_config.py}.
 */
public class JiuwenConfig {

    private boolean enabled = true;
    private Integer agentServerPort;
    private String appHost = "127.0.0.1";
    private Integer wsPort;
    private String webHost = "127.0.0.1";
    private Integer webPort;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Integer getAgentServerPort() { return agentServerPort; }
    public void setAgentServerPort(Integer agentServerPort) { 
        if (agentServerPort != null && (agentServerPort < 1 || agentServerPort > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.agentServerPort = agentServerPort; 
    }
    public String getAppHost() { return appHost; }
    public void setAppHost(String appHost) { this.appHost = appHost; }
    public Integer getWsPort() { return wsPort; }
    public void setWsPort(Integer wsPort) { 
        if (wsPort != null && (wsPort < 1 || wsPort > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.wsPort = wsPort; 
    }
    public String getWebHost() { return webHost; }
    public void setWebHost(String webHost) { this.webHost = webHost; }
    public Integer getWebPort() { return webPort; }
    public void setWebPort(Integer webPort) { 
        if (webPort != null && (webPort < 1 || webPort > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.webPort = webPort; 
    }
}

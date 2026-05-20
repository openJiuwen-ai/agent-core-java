/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.JiuwenConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiuwenConfig {
    private boolean isEnabled = true;
    private Integer agentServerPort;
    private String appHost = "127.0.0.1";
    private Integer wsPort;
    private String webHost = "127.0.0.1";
    private Integer webPort;

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        VLLMServiceConfig.validateOptionalPort(agentServerPort, "jiuwen.agent_server_port");
        VLLMServiceConfig.validateOptionalPort(wsPort, "jiuwen.ws_port");
        VLLMServiceConfig.validateOptionalPort(webPort, "jiuwen.web_port");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getAgent_server_port() { return getAgentServerPort(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAgent_server_port(Integer value) { setAgentServerPort(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApp_host() { return getAppHost(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApp_host(String value) { setAppHost(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getWs_port() { return getWsPort(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWs_port(Integer value) { setWsPort(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWeb_host() { return getWebHost(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWeb_host(String value) { setWebHost(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getWeb_port() { return getWebPort(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWeb_port(Integer value) { setWebPort(value); }
}

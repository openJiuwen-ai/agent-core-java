/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.JiuwenConfig.
 * 
 * @since 0.1.7
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
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        VLLMServiceConfig.validateOptionalPort(agentServerPort, "jiuwen.agent_server_port");
        VLLMServiceConfig.validateOptionalPort(wsPort, "jiuwen.ws_port");
        VLLMServiceConfig.validateOptionalPort(webPort, "jiuwen.web_port");
    }

    /**
     * getAgent_server_port.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getAgent_server_port() {
        return getAgentServerPort();
    }

    /**
     * setAgent_server_port.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setAgent_server_port(Integer value) {
        setAgentServerPort(value);
    }

    /**
     * getApp_host.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApp_host() {
        return getAppHost();
    }

    /**
     * setApp_host.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setApp_host(String value) {
        setAppHost(value);
    }

    /**
     * getWs_port.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getWs_port() {
        return getWsPort();
    }

    /**
     * setWs_port.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setWs_port(Integer value) {
        setWsPort(value);
    }

    /**
     * getWeb_host.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWeb_host() {
        return getWebHost();
    }

    /**
     * setWeb_host.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setWeb_host(String value) {
        setWebHost(value);
    }

    /**
     * getWeb_port.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getWeb_port() {
        return getWebPort();
    }

    /**
     * setWeb_port.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setWeb_port(Integer value) {
        setWebPort(value);
    }
}

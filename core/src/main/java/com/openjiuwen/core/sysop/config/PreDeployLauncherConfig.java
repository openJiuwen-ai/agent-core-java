/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for a pre-existing sandbox reachable via HTTP or WebSocket.
 * <p>
 * Mirrors Python's {@code PreDeployLauncherConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreDeployLauncherConfig extends SandboxLauncherConfig {

    @JsonProperty("base_url")
    private String baseUrl;

    public PreDeployLauncherConfig() {
        super();
        setLauncherType("pre_deploy");
        setSandboxType("aio");
    }

    public PreDeployLauncherConfig(String baseUrl) {
        this();
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}

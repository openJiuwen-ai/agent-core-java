/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code InitializeOptions} in
 * {@code openjiuwen/harness/lsp/types.py}.
 */
public class InitializeOptions {

    private String cwd;

    @JsonProperty("custom_servers")
    private Map<String, CustomServerConfig> customServers;

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public Map<String, CustomServerConfig> getCustomServers() {
        return customServers;
    }

    public void setCustomServers(Map<String, CustomServerConfig> customServers) {
        this.customServers = customServers == null ? null : new LinkedHashMap<>(customServers);
    }
}

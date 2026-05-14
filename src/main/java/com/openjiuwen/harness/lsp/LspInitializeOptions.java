/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal initialization options for Java harness LSP.
 *
 * <p>Mirrors Python's {@code InitializeOptions} in
 * {@code openjiuwen.harness.lsp.types}.
 */
public class LspInitializeOptions {

    private String cwd;
    private Map<String, CustomServerConfig> customServers = new LinkedHashMap<>();

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
        this.customServers = customServers != null ? new LinkedHashMap<>(customServers) : new LinkedHashMap<>();
    }
}

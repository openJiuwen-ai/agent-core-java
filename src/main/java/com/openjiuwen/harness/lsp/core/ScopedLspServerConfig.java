/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal scoped config for one Java harness LSP server instance.
 *
 * <p>Mirrors Python's {@code ScopedLspServerConfig} in
 * {@code openjiuwen.harness.lsp.core.types}.
 */
public class ScopedLspServerConfig {

    private String serverId;
    private String command;
    private String workspaceFolder;
    private List<String> args = List.of();
    private Map<String, String> env = new LinkedHashMap<>();
    private Map<String, Object> initializationOptions = new LinkedHashMap<>();
    private int startupTimeout = 45_000;
    private Map<String, String> extensionToLanguage = new LinkedHashMap<>();

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getWorkspaceFolder() {
        return workspaceFolder;
    }

    public void setWorkspaceFolder(String workspaceFolder) {
        this.workspaceFolder = workspaceFolder;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args != null ? List.copyOf(args) : List.of();
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env != null ? new LinkedHashMap<>(env) : new LinkedHashMap<>();
    }

    public Map<String, Object> getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(Map<String, Object> initializationOptions) {
        this.initializationOptions = initializationOptions != null
                ? new LinkedHashMap<>(initializationOptions) : new LinkedHashMap<>();
    }

    public int getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(int startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    public Map<String, String> getExtensionToLanguage() {
        return extensionToLanguage;
    }

    public void setExtensionToLanguage(Map<String, String> extensionToLanguage) {
        this.extensionToLanguage = extensionToLanguage != null
                ? new LinkedHashMap<>(extensionToLanguage) : new LinkedHashMap<>();
    }
}

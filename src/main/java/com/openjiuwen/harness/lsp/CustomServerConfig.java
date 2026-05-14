/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal custom server configuration override for Java harness LSP.
 *
 * <p>Mirrors Python's {@code CustomServerConfig} in
 * {@code openjiuwen.harness.lsp.types}.
 */
public class CustomServerConfig {

    private String command;
    private List<String> args = List.of();
    private Map<String, String> env = new LinkedHashMap<>();
    private List<String> extensions = List.of();
    private String languageId;
    private Map<String, Object> initializationOptions = new LinkedHashMap<>();
    private boolean disabled;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
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

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions != null ? List.copyOf(extensions) : List.of();
    }

    public String getLanguageId() {
        return languageId;
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public Map<String, Object> getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(Map<String, Object> initializationOptions) {
        this.initializationOptions = initializationOptions != null
                ? new LinkedHashMap<>(initializationOptions) : new LinkedHashMap<>();
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}

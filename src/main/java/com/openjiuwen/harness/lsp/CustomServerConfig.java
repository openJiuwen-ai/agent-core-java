/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code CustomServerConfig} in
 * {@code openjiuwen/harness/lsp/types.py}.
 */
public class CustomServerConfig {

    private String command;
    private List<String> args;
    private Map<String, String> env;
    private List<String> extensions;

    @JsonProperty("language_id")
    private String languageId;

    @JsonProperty("initialization_options")
    private Map<String, Object> initializationOptions;

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
        this.args = args == null ? null : List.copyOf(args);
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? null : new LinkedHashMap<>(env);
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? null : List.copyOf(extensions);
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
        this.initializationOptions = initializationOptions == null ? null : new LinkedHashMap<>(initializationOptions);
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}

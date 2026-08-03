/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code SpawnHandle} in
 * {@code openjiuwen/harness/lsp/core/types.py}.
 */
public class SpawnHandle {

    private String command;
    private List<String> args = new ArrayList<>();
    private Map<String, String> env = new LinkedHashMap<>();

    @JsonProperty("initialization_options")
    private Map<String, Object> initializationOptions;

    @JsonProperty("startup_timeout")
    private int startupTimeout = 45_000;

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
        this.args = args == null ? new ArrayList<>() : new ArrayList<>(args);
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(env);
    }

    public Map<String, Object> getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(Map<String, Object> initializationOptions) {
        this.initializationOptions = initializationOptions == null ? null : new LinkedHashMap<>(initializationOptions);
    }

    public int getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(int startupTimeout) {
        this.startupTimeout = startupTimeout;
    }
}

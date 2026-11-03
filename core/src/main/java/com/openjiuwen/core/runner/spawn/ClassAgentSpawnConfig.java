/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-safe config for constructing an agent class in a child process.
 *
 * <p>Mirrors Python's {@code ClassAgentSpawnConfig} in
 * {@code openjiuwen/core/runner/spawn/agent_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassAgentSpawnConfig extends SpawnAgentConfig {

    @JsonProperty("agent_module")
    private String agentModule;

    @JsonProperty("agent_class")
    private String agentClass;

    @JsonProperty("init_kwargs")
    private Map<String, Object> initKwargs = new LinkedHashMap<>();

    public ClassAgentSpawnConfig() {
        setAgentKind(SpawnAgentKind.CLASS_AGENT);
    }

    public ClassAgentSpawnConfig(String agentModule, String agentClass) {
        this();
        this.agentModule = agentModule;
        this.agentClass = agentClass;
    }

    public String getAgentModule() {
        return agentModule;
    }

    public void setAgentModule(String agentModule) {
        this.agentModule = agentModule;
    }

    public String getAgentClass() {
        return agentClass;
    }

    public void setAgentClass(String agentClass) {
        this.agentClass = agentClass;
    }

    public Map<String, Object> getInitKwargs() {
        return new LinkedHashMap<>(initKwargs);
    }

    public void setInitKwargs(Map<String, Object> initKwargs) {
        this.initKwargs = initKwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initKwargs);
    }
}

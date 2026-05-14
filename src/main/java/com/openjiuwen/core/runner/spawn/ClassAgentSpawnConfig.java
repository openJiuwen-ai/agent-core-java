/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON-safe config for constructing an agent class in the child process via reflection.
 * <p>
 * Mirrors Python's {@code ClassAgentSpawnConfig} in {@code runner/spawn/agent_config.py}.
 */
public class ClassAgentSpawnConfig extends SpawnAgentConfig {

    private String agentModule;
    private String agentClass;
    private Map<String, Object> initKwargs;

    public ClassAgentSpawnConfig() {
        super();
        setAgentKind(SpawnAgentKind.CLASS_AGENT);
        this.initKwargs = new HashMap<>();
    }

    public ClassAgentSpawnConfig(String agentModule, String agentClass,
                                 Map<String, Object> initKwargs) {
        super();
        setAgentKind(SpawnAgentKind.CLASS_AGENT);
        this.agentModule = agentModule;
        this.agentClass = agentClass;
        this.initKwargs = initKwargs != null ? initKwargs : new HashMap<>();
    }

    /**
     * Deserialize from a JSON-safe map, including class-agent-specific fields.
     *
     * @param data the map representation
     * @return the parsed config
     */
    public static ClassAgentSpawnConfig fromMap(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> runnerConfig = (Map<String, Object>) data.get("runner_config");
        @SuppressWarnings("unchecked")
        Map<String, Object> loggingConfig = (Map<String, Object>) data.get("logging_config");
        String sessionId = (String) data.get("session_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) data.get("payload");

        ClassAgentSpawnConfig config = new ClassAgentSpawnConfig();
        config.setAgentKind(SpawnAgentKind.CLASS_AGENT);
        config.setRunnerConfig(runnerConfig);
        config.setLoggingConfig(loggingConfig);
        config.setSessionId(sessionId);
        config.setPayload(payload);

        config.agentModule = (String) data.get("agent_module");
        config.agentClass = (String) data.get("agent_class");
        @SuppressWarnings("unchecked")
        Map<String, Object> initKwargs = (Map<String, Object>) data.get("init_kwargs");
        config.initKwargs = initKwargs != null ? initKwargs : new HashMap<>();

        return config;
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
        return initKwargs;
    }

    public void setInitKwargs(Map<String, Object> initKwargs) {
        this.initKwargs = initKwargs;
    }
}

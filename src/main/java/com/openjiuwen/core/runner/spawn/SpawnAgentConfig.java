/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import java.util.HashMap;
import java.util.Map;

/**
 * Base spawn config shared by all child-process agent bootstraps.
 * <p>
 * Mirrors Python's {@code SpawnAgentConfig} in {@code runner/spawn/agent_config.py}.
 */
public class SpawnAgentConfig {

    private SpawnAgentKind agentKind;
    private Map<String, Object> runnerConfig;
    private Map<String, Object> loggingConfig;
    private String sessionId;
    private Map<String, Object> payload;

    public SpawnAgentConfig() {
        this.payload = new HashMap<>();
    }

    public SpawnAgentConfig(SpawnAgentKind agentKind, Map<String, Object> runnerConfig,
                            Map<String, Object> loggingConfig, String sessionId,
                            Map<String, Object> payload) {
        this.agentKind = agentKind;
        this.runnerConfig = runnerConfig;
        this.loggingConfig = loggingConfig;
        this.sessionId = sessionId;
        this.payload = payload != null ? payload : new HashMap<>();
    }

    /**
     * Deserialize from a JSON-safe map.
     *
     * @param data the map representation
     * @return the parsed config
     */
    public static SpawnAgentConfig fromMap(Map<String, Object> data) {
        if (data == null) {
            return new SpawnAgentConfig();
        }
        String kindStr = (String) data.get("agent_kind");
        SpawnAgentKind kind = kindStr != null ? SpawnAgentKind.fromValue(kindStr) : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> runnerConfig = (Map<String, Object>) data.get("runner_config");
        @SuppressWarnings("unchecked")
        Map<String, Object> loggingConfig = (Map<String, Object>) data.get("logging_config");
        String sessionId = (String) data.get("session_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) data.get("payload");

        if (SpawnAgentKind.CLASS_AGENT.getValue().equals(kindStr)) {
            return ClassAgentSpawnConfig.fromMap(data);
        }

        return new SpawnAgentConfig(kind, runnerConfig, loggingConfig, sessionId, payload);
    }

    public SpawnAgentKind getAgentKind() {
        return agentKind;
    }

    public void setAgentKind(SpawnAgentKind agentKind) {
        this.agentKind = agentKind;
    }

    public Map<String, Object> getRunnerConfig() {
        return runnerConfig;
    }

    public void setRunnerConfig(Map<String, Object> runnerConfig) {
        this.runnerConfig = runnerConfig;
    }

    public Map<String, Object> getLoggingConfig() {
        return loggingConfig;
    }

    public void setLoggingConfig(Map<String, Object> loggingConfig) {
        this.loggingConfig = loggingConfig;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

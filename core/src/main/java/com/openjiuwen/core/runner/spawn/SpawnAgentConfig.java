/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base spawn config shared by child-process agent bootstraps.
 *
 * <p>Mirrors Python's {@code SpawnAgentConfig} in
 * {@code openjiuwen/core/runner/spawn/agent_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpawnAgentConfig {

    @JsonProperty("agent_kind")
    private SpawnAgentKind agentKind;

    @JsonProperty("runner_config")
    private Map<String, Object> runnerConfig;

    @JsonProperty("logging_config")
    private Map<String, Object> loggingConfig;

    @JsonProperty("session_id")
    private String sessionId;

    private Map<String, Object> payload = new LinkedHashMap<>();
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public SpawnAgentConfig() {
    }

    public SpawnAgentConfig(SpawnAgentKind agentKind) {
        this.agentKind = agentKind;
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
        this.runnerConfig = copyNullable(runnerConfig);
    }

    public Map<String, Object> getLoggingConfig() {
        return loggingConfig;
    }

    public void setLoggingConfig(Map<String, Object> loggingConfig) {
        this.loggingConfig = copyNullable(loggingConfig);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getPayload() {
        return new LinkedHashMap<>(payload);
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return new LinkedHashMap<>(extraFields);
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        if (!isKnownField(key)) {
            extraFields.put(key, value);
        }
    }

    public Map<String, Object> toMap() {
        return SpawnAgentConfigs.toMap(this);
    }

    protected void copyBaseTo(SpawnAgentConfig target) {
        target.setAgentKind(agentKind);
        target.setRunnerConfig(runnerConfig);
        target.setLoggingConfig(loggingConfig);
        target.setSessionId(sessionId);
        target.setPayload(payload);
        extraFields.forEach(target::putExtraField);
    }

    private static Map<String, Object> copyNullable(Map<String, Object> value) {
        return value == null ? null : new LinkedHashMap<>(value);
    }

    private static boolean isKnownField(String key) {
        return "agent_kind".equals(key)
                || "runner_config".equals(key)
                || "logging_config".equals(key)
                || "session_id".equals(key)
                || "payload".equals(key);
    }
}

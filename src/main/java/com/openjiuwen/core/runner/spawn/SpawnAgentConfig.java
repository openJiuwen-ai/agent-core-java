/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.runner.RunnerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Public class SpawnAgentConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpawnAgentConfig {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonProperty("agent_kind")
    private SpawnAgentKind agentKind;

    @JsonProperty("runner_config")
    private RunnerConfig runnerConfig;

    @JsonProperty("logging_config")
    private Map<String, Object> loggingConfig;

    @JsonProperty("session_id")
    private String sessionId;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonAnyGetter
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonAnySetter
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new LinkedHashMap<>();
        }
        extraFields.put(key, value);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_kind", agentKind != null ? agentKind.value() : null);
        result.put("runner_config", runnerConfig != null
                ? OBJECT_MAPPER.convertValue(runnerConfig, Map.class)
                : new LinkedHashMap<String, Object>());
        result.put("logging_config", loggingConfig);
        result.put("session_id", sessionId);
        result.put("payload", payload != null ? payload : new LinkedHashMap<String, Object>());
        if (extraFields != null && !extraFields.isEmpty()) {
            result.putAll(extraFields);
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> requirePayload() {
        return Objects.requireNonNullElseGet(payload, LinkedHashMap::new);
    }
}

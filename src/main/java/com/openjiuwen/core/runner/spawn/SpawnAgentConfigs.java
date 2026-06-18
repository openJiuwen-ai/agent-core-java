/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.RunnerConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module-level helpers for spawn agent config parsing and runner config JSON conversion.
 *
 * <p>Mirrors Python's {@code serialize_runner_config}, {@code deserialize_runner_config}, and
 * {@code parse_spawn_agent_config} in
 * {@code openjiuwen/core/runner/spawn/agent_config.py}.</p>
 */
public final class SpawnAgentConfigs {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private SpawnAgentConfigs() {
    }

    /**
     * Serializes RunnerConfig to a JSON-safe dictionary.
     *
     * @param config runner config
     * @return JSON-safe map
     */
    public static Map<String, Object> serializeRunnerConfig(RunnerConfig config) {
        if (config == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(config, STRING_OBJECT_MAP);
    }

    /**
     * Rebuilds RunnerConfig from a JSON-safe payload.
     *
     * @param payload JSON-safe runner config map
     * @return runner config instance
     */
    public static RunnerConfig deserializeRunnerConfig(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(payload, RunnerConfig.class);
    }

    /**
     * Validates spawn config with the schema matching agent_kind.
     *
     * @param payload JSON-safe spawn config map
     * @return parsed config subtype
     */
    public static SpawnAgentConfig parseSpawnAgentConfig(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("spawn agent config payload cannot be null");
        }
        Object agentKind = payload.get("agent_kind");
        if (SpawnAgentKind.CLASS_AGENT.getValue().equals(String.valueOf(agentKind))) {
            return OBJECT_MAPPER.convertValue(payload, ClassAgentSpawnConfig.class);
        }
        return OBJECT_MAPPER.convertValue(payload, SpawnAgentConfig.class);
    }

    static Map<String, Object> toMap(SpawnAgentConfig config) {
        if (config == null) {
            return null;
        }
        return new LinkedHashMap<>(OBJECT_MAPPER.convertValue(config, STRING_OBJECT_MAP));
    }
}

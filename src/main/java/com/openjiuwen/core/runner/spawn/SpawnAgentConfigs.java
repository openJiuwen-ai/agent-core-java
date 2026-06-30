/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class SpawnAgentConfigs {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SpawnAgentConfigs() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static SpawnAgentConfig parseSpawnAgentConfig(Map<String, Object> payload) {
        Object rawKind = payload.get("agent_kind");
        SpawnAgentKind kind = rawKind != null ? SpawnAgentKind.fromValue(String.valueOf(rawKind)) : null;
        Class<? extends SpawnAgentConfig> targetClass = kind == SpawnAgentKind.CLASS_AGENT
                ? ClassAgentSpawnConfig.class
                : SpawnAgentConfig.class;
        return OBJECT_MAPPER.convertValue(payload, targetClass);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported spawned-agent bootstrap kinds.
 *
 * <p>Mirrors Python's {@code SpawnAgentKind} in
 * {@code openjiuwen/core/runner/spawn/agent_config.py}.</p>
 */
public enum SpawnAgentKind {
    CLASS_AGENT("class_agent"),
    TEAM_AGENT("team_agent");

    private final String value;

    SpawnAgentKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SpawnAgentKind fromValue(String value) {
        for (SpawnAgentKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown spawn agent kind: " + value);
    }
}

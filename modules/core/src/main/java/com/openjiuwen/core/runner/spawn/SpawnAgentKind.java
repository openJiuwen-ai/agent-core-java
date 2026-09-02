/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Public enum SpawnAgentKind used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum SpawnAgentKind {
    CLASS_AGENT("class_agent"),
    TEAM_AGENT("team_agent");

    private final String value;

    SpawnAgentKind(String value) {
        this.value = value;
    }

    /**
     * value.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @JsonCreator
    public static SpawnAgentKind fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SpawnAgentKind kind : values()) {
            if (kind.value.equals(normalized) || kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown spawn agent kind: " + value);
    }
}

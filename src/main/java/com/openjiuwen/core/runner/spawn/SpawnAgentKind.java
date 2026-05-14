/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Supported spawned-agent bootstrap kinds.
 * <p>
 * Mirrors Python's {@code SpawnAgentKind} in {@code runner/spawn/agent_config.py}.
 */
public enum SpawnAgentKind {

    /** Bootstrap by constructing a class via reflection. */
    CLASS_AGENT("class_agent"),

    /** Bootstrap a team-oriented agent. */
    TEAM_AGENT("team_agent");

    private final String value;

    SpawnAgentKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string value into a SpawnAgentKind.
     *
     * @param value the string value
     * @return the matching SpawnAgentKind
     * @throws IllegalArgumentException if no matching kind exists
     */
    public static SpawnAgentKind fromValue(String value) {
        for (SpawnAgentKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown SpawnAgentKind: " + value);
    }
}

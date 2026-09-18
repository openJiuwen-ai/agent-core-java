/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Topic categories for team event routing.
 * <p>
 * Mirrors Python's {@code TeamTopic} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
public enum TeamTopic {
    TEAM("team"),
    TASK("task"),
    MESSAGE("message");

    private final String value;

    TeamTopic(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String build(String sessionId, String teamName) {
        return "session:" + sessionId + ":team:" + teamName + ":" + value;
    }

    @JsonCreator
    public static TeamTopic fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TeamTopic topic : values()) {
            if (topic.value.equals(value)) {
                return topic;
            }
        }
        return null;
    }
}

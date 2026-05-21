// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Topic categories for team event routing.
 * 
 * Mirrors Python's agent_teams.schema.events.TeamTopic
 * 
 * @since 0.1.12
 */
public enum TeamTopic {
    TEAM("team"),
    TASK("task"),
    MESSAGE("message");

    private final String value;

    TeamTopic(String value) {
        this.value = value;
    }

    /**
     * Get the string value of this topic.
     * 
     * @return The string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Build the final topic string.
     * 
     * @param sessionId The session identifier
     * @param teamName The team identifier (human-chosen unique name)
     * @return Topic string in the format "session:{sessionId}:team:{teamName}:{topic}"
     */
    public String build(String sessionId, String teamName) {
        return String.format("session:%s:team:%s:%s", sessionId, teamName, value);
    }

    /**
     * Parse a string to a TeamTopic.
     * 
     * @param value The string value to parse
     * @return The corresponding TeamTopic, or null if not found
     */
    public static TeamTopic fromValue(String value) {
        for (TeamTopic topic : TeamTopic.values()) {
            if (topic.value.equals(value)) {
                return topic;
            }
        }
        return null;
    }
}
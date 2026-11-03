/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.events;

/**
 * Topic categories for team event routing.
 *
 * <p>Mirrors Python {@code schema/events.py:TeamTopic} (str-Enum). Each value
 * builds a final topic string of the form
 * {@code "session:<sid>:team:<team>:<topic>"} for messager subscription.
 *
 * @since 2026/7/9
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
     * Return the full topic string.
     *
     * @return the topic string
     */
    public String value() {
        return value;
    }

    /**
     * Build the final topic string.
     *
     * @param sessionId session identifier
     * @param teamName team identifier (human-chosen unique name)
     * @return {@code "session:<sid>:team:<team>:<topic>"}
     */
    public String build(String sessionId, String teamName) {
        return "session:" + sessionId + ":team:" + teamName + ":" + value;
    }
}

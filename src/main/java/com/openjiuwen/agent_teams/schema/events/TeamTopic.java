/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.events;

/**
 * Minimal team topic enum.
 *
 * <p>Mirrors Python's {@code TeamTopic} in
 * {@code openjiuwen.agent_teams.schema.events}.
 */
public enum TeamTopic {
    TEAM,
    TASK,
    MESSAGE;

    public String build(String sessionId, String teamName) {
        return "session:" + sessionId + ":team:" + teamName + ":" + name().toLowerCase();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal coordination event envelope.
 *
 * <p>Mirrors Python's coordination-event intent in
 * {@code openjiuwen.agent_teams.agent.coordinator}.
 */
public class CoordinationEvent {

    private final String eventType;
    private final Map<String, Object> payload;

    public CoordinationEvent(String eventType, Map<String, Object> payload) {
        this.eventType = eventType != null ? eventType : "unknown";
        this.payload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return new LinkedHashMap<>(payload);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.events;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal event envelope for team messager transports.
 *
 * <p>Mirrors Python's {@code EventMessage} in
 * {@code openjiuwen.agent_teams.schema.events}.</p>
 */
public class EventMessage {

    private final String eventType;
    private final Map<String, Object> payload;
    private String senderId = "";

    public EventMessage(String eventType, Map<String, Object> payload) {
        this.eventType = eventType != null ? eventType : "";
        this.payload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return new LinkedHashMap<>(payload);
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId != null ? senderId : "";
    }
}

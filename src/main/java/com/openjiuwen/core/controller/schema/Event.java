/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event base class.
 *
 * <p>Mirrors Python's {@code Event} in
 * {@code openjiuwen/core/controller/schema/event.py}.</p>
 */
public class Event {

    @JsonProperty("event_type")
    private EventType eventType;

    @JsonProperty("event_id")
    private String eventId;

    private Map<String, Object> metadata;

    public Event() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new LinkedHashMap<>();
    }

    public Event(EventType eventType) {
        this();
        this.eventType = eventType;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }
}

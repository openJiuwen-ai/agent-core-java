/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event class hierarchy for the controller module.
 * <p>
 * Base class for all events, with specialized subclasses:
 * <ul>
 *   <li>{@link InputEvent} - user input event</li>
 *   <li>{@link TaskInteractionEvent} - task interaction event</li>
 *   <li>{@link TaskCompletionEvent} - task completion event</li>
 *   <li>{@link TaskFailedEvent} - task failed event</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code Event} base class and its subclasses.
 */
public class Event {

    private EventType eventType;
    private String eventId;
    private Map<String, Object> metadata;

    public Event() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
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
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}

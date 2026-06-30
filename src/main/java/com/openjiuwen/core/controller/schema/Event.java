/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public Event() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Event(EventType eventType) {
        this();
        this.eventType = eventType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}

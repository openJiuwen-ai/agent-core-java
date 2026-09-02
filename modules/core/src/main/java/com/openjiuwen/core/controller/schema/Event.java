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
 * <li>{@link InputEvent} - user input event</li>
 * <li>{@link TaskInteractionEvent} - task interaction event</li>
 * <li>{@link TaskCompletionEvent} - task completion event</li>
 * <li>{@link TaskFailedEvent} - task failed event</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code Event} base class and its subclasses.
 * 
 * @since 0.1.7
 */
public class Event {
    private EventType eventType;
    private String eventId;
    private Map<String, Object> metadata;

    /**
     * Event.
     * 
     * @since 0.1.7
     */
    public Event() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
    }

    /**
     * Event.
     * 
     * @param eventType eventType
     * @since 0.1.7
     */
    public Event(EventType eventType) {
        this();
        this.eventType = eventType;
    }

    /**
     * getEventType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * setEventType.
     * 
     * @param eventType eventType
     * @since 0.1.7
     */
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    /**
     * getEventId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * setEventId.
     * 
     * @param eventId eventId
     * @since 0.1.7
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}

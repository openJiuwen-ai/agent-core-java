// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Event Base Class.
 *
 * <p>Base class for all events, containing event type, event ID, and metadata.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Event {

    private final EventType eventType;
    private final String eventId;
    private Map<String, Object> metadata;

    /**
     * Constructor with event type only. Generates a random event ID.
     *
     * @param eventType the event type
     */
    public Event(EventType eventType) {
        this(eventType, null, null);
    }

    /**
     * Full constructor.
     *
     * @param eventType the event type
     * @param eventId   the event ID (if null, a UUID is generated)
     * @param metadata  the metadata (can be null)
     */
    public Event(EventType eventType, String eventId, Map<String, Object> metadata) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.eventId = (eventId != null && !eventId.isBlank()) ? eventId : UUID.randomUUID().toString();
        this.metadata = metadata;
    }

    /**
     * Gets the event type.
     *
     * @return the event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Gets the event ID.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Gets the metadata.
     *
     * @return the metadata map, or null
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata.
     *
     * @param metadata the metadata map
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}


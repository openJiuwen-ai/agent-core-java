// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

/**
 * Event Type Enumeration.
 *
 * <p>Defines all supported event types:
 * <ul>
 *   <li>INPUT: User input event</li>
 *   <li>TASK_INTERACTION: Task interaction event (requires user interaction during task execution)</li>
 *   <li>TASK_COMPLETION: Task completion event</li>
 *   <li>TASK_FAILED: Task failed event</li>
 * </ul>
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum EventType {

    INPUT("input"),
    TASK_INTERACTION("task_interaction"),
    TASK_COMPLETION("task_completion"),
    TASK_FAILED("task_failed");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the event type.
     *
     * @return the event type value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the EventType from its string value.
     *
     * @param value the string value
     * @return the corresponding EventType
     * @throws IllegalArgumentException if value is not recognized
     */
    public static EventType fromValue(String value) {
        for (EventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType value: " + value);
    }
}


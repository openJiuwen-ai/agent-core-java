/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

/**
 * Event type enumeration.
 * <p>
 * Defines all supported event types:
 * <ul>
 * <li>INPUT - user input event</li>
 * <li>TASK_INTERACTION - task interaction event</li>
 * <li>TASK_COMPLETION - task completion event</li>
 * <li>TASK_FAILED - task failed event</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code EventType(str, Enum)}.
 * 
 * @since 0.1.7
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
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static EventType fromValue(String value) {
        for (EventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType: " + value);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return value;
    }
}

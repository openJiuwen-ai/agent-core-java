/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Event type enumeration.
 * <p>
 * Defines all supported event types:
 * <ul>
 *   <li>INPUT - user input event</li>
 *   <li>TASK_INTERACTION - task interaction event</li>
 *   <li>TASK_COMPLETION - task completion event</li>
 *   <li>TASK_FAILED - task failed event</li>
 *   <li>FOLLOW_UP - follow-up event for continuing task loop</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code EventType} in
 * {@code openjiuwen/core/controller/schema/event.py}.
 */
public enum EventType {

    INPUT("input"),
    TASK_INTERACTION("task_interaction"),
    TASK_COMPLETION("task_completion"),
    TASK_FAILED("task_failed"),
    FOLLOW_UP("follow_up");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EventType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

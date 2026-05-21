/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Event types consumed by the outer task-loop.
 *
 * <p>Mirrors Python's {@code DeepLoopEventType} in
 * {@code openjiuwen.harness.schema.loop_event}.
 */
public enum DeepLoopEventType {

    /** Followup event (lowest priority). */
    FOLLOWUP("followup", 10),

    /** Steering event (medium priority). */
    STEER("steer", 1),

    /** Abort event (highest priority). */
    ABORT("abort", 0);

    private final String value;
    private final int defaultPriority;

    DeepLoopEventType(String value, int defaultPriority) {
        this.value = value;
        this.defaultPriority = defaultPriority;
    }

    public String getValue() {
        return value;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    /**
     * Parse from string value.
     */
    public static DeepLoopEventType fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return FOLLOWUP;
        }
        for (DeepLoopEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return FOLLOWUP;
    }
}
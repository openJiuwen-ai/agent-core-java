/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Message queue type enumeration.
 *
 * <p>Mirrors Python's {@code MessageQueueType} in
 * {@code openjiuwen/core/runner/runner_config.py}.</p>
 */
public enum MessageQueueType {
    PULSAR("pulsar"),
    FAKE("fake");

    private final String value;

    MessageQueueType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static MessageQueueType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (MessageQueueType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message queue type: " + value);
    }
}

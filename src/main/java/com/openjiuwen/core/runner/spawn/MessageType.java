/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Public enum MessageType used by the Java parity implementation.
 *
 * @since 1.0
 */
public enum MessageType {
    INPUT("INPUT"),
    OUTPUT("OUTPUT"),
    HEALTH_CHECK("HEALTH_CHECK"),
    HEALTH_CHECK_RESPONSE("HEALTH_CHECK_RESPONSE"),
    SHUTDOWN("SHUTDOWN"),
    SHUTDOWN_ACK("SHUTDOWN_ACK"),
    ERROR("ERROR"),
    STREAM_CHUNK("STREAM_CHUNK"),
    DONE("DONE");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonValue
    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonCreator
    /**
     * Auto-generated for codecheck compliance.
     */
    public static MessageType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MessageType type : values()) {
            if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown spawn message type: " + value);
    }
}

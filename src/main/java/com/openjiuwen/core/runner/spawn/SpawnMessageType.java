/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Message types for stdio communication protocol between parent and spawned child processes.
 * <p>
 * Mirrors Python's {@code MessageType} in {@code runner/spawn/protocol.py}.
 */
public enum SpawnMessageType {

    /** Input payload sent from parent to child. */
    INPUT("INPUT"),

    /** Output payload sent from child to parent. */
    OUTPUT("OUTPUT"),

    /** Health check request from parent. */
    HEALTH_CHECK("HEALTH_CHECK"),

    /** Health check response from child. */
    HEALTH_CHECK_RESPONSE("HEALTH_CHECK_RESPONSE"),

    /** Shutdown command from parent. */
    SHUTDOWN("SHUTDOWN"),

    /** Shutdown acknowledgement from child. */
    SHUTDOWN_ACK("SHUTDOWN_ACK"),

    /** Error notification. */
    ERROR("ERROR"),

    /** Streaming chunk (partial output). */
    STREAM_CHUNK("STREAM_CHUNK"),

    /** Completion signal. */
    DONE("DONE");

    private final String value;

    SpawnMessageType(String value) {
        this.value = value;
    }

    /**
     * Get the protocol string value.
     *
     * @return the string representation used in JSON serialization
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string value into a MessageType.
     *
     * @param value the string value
     * @return the matching MessageType
     * @throws IllegalArgumentException if no matching type exists
     */
    public static SpawnMessageType fromValue(String value) {
        for (SpawnMessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SpawnMessageType: " + value);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Message types for stdio communication protocol.
 *
 * <p>Mirrors Python's {@code MessageType} in
 * {@code openjiuwen/core/runner/spawn/protocol.py}.</p>
 */
public enum SpawnMessageType {
    INPUT,
    OUTPUT,
    HEALTH_CHECK,
    HEALTH_CHECK_RESPONSE,
    SHUTDOWN,
    SHUTDOWN_ACK,
    ERROR,
    STREAM_CHUNK,
    DONE;

    public static SpawnMessageType fromValue(String value) {
        return SpawnMessageType.valueOf(value);
    }
}

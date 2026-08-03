/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Mirrors Python's {@code StreamEvent} in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
 */
public final class StreamEvent {

    private final StreamEventType type;
    private final Object data;
    private final OffsetDateTime timestamp;

    public StreamEvent(StreamEventType type, Object data, OffsetDateTime timestamp) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = data;
        this.timestamp = timestamp != null ? timestamp : OffsetDateTime.now(ZoneOffset.UTC);
    }

    public StreamEvent(StreamEventType type, Object data) {
        this(type, data, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public StreamEventType getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Heartbeat trigger reason.
 *
 * <p>Mirrors Python's {@code HeartbeatReason} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public enum HeartbeatReason {
    INTERVAL("interval"),
    MANUAL("manual");

    private final String value;

    HeartbeatReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static HeartbeatReason fromValue(String value) {
        for (HeartbeatReason item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}

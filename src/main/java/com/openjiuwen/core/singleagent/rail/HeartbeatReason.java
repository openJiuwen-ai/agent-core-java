/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Heartbeat trigger reason.
 *
 * <p>Mirrors Python's {@code HeartbeatReason} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
public enum HeartbeatReason {
    INTERVAL("interval"),
    MANUAL("manual");

    private final String value;

    HeartbeatReason(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

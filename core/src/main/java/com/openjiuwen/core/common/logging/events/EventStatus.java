/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

/**
 * Event status enumeration.
 *
 * <p>Mirrors Python's {@code EventStatus} in
 * {@code openjiuwen/core/common/logging/events.py}.</p>
 */
public enum EventStatus {
    SUCCESS("success"),
    FAILURE("failure"),
    PENDING("pending"),
    TIMEOUT("timeout"),
    CANCELLED("cancelled");

    private final String value;

    EventStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}



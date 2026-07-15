/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

/**
 * Event status enumeration.
 * 
 * @since 0.1.7
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

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}

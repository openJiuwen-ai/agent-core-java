/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

/**
 * Enumeration of stream event types for process output monitoring.
 * <p>
 * Mirrors Python's {@code StreamEventType} in {@code local/utils.py}.
 */
public enum StreamEventType {
    STDOUT("stdout"),
    STDERR("stderr"),
    EXIT("exit"),
    ERROR("error");

    private final String value;

    StreamEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

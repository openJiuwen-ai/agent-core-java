/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

/**
 * Mirrors Python's {@code StreamEventType} in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
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

// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

/**
 * 事件状态枚举
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
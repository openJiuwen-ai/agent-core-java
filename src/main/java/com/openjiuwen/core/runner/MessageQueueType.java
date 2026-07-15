/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

/**
 * Message queue type enumeration.
 * 
 * @since 0.1.7
 */
public enum MessageQueueType {
    PULSAR("pulsar"),
    FAKE("fake");

    private final String value;

    MessageQueueType(String value) {
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

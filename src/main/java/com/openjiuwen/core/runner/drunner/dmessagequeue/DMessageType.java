// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

/**
 * Distributed message type.
 * 
 * 对应Python: drunner/dmessage_queue/message.py - DMessageType
 */
public enum DMessageType {
    INPUT("INPUT"),
    STOP("STOP"),
    OUTPUT("OUTPUT");

    private final String value;

    DMessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}


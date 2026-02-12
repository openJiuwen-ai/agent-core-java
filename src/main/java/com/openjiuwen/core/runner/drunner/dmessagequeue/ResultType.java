// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

/**
 * Result type for distributed messages.
 * 
 * 对应Python: drunner/dmessage_queue/message.py - ResultType
 */
public enum ResultType {
    MESSAGE("MESSAGE"),
    ERROR("ERROR");

    private final String value;

    ResultType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}


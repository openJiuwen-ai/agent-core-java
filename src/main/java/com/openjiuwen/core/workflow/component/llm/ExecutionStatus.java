/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner state machine execution status.
 */
public enum ExecutionStatus {
    START("start"),
    USER_INTERACT("user_interact"),
    END("end");

    private final String value;

    ExecutionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ExecutionStatus fromValue(String value) {
        for (ExecutionStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown execution status: " + value);
    }
}

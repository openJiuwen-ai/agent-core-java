/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Questioner state machine execution status.
 * 
 * @since 0.1.7
 */
public enum ExecutionStatus {
    START("start"),
    USER_INTERACT("user_interact"),
    END("end");

    private final String value;

    ExecutionStatus(String value) {
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

    /**
     * fromValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ExecutionStatus fromValue(String value) {
        for (ExecutionStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown execution status: " + value);
    }
}

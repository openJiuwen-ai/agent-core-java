/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

/**
 * Task status enumeration.
 * <p>
 * Task status flow:
 * {@code submitted -> working -> (completed | failed | paused | canceled)}
 * {@code working -> input-required -> (continue execution or cancel)}
 * <p>
 * Mirrors Python's {@code TaskStatus(str, Enum)}.
 */
public enum TaskStatus {

    SUBMITTED("submitted"),
    WORKING("working"),
    PAUSED("paused"),
    INPUT_REQUIRED("input-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    WAITING("waiting"),
    UNKNOWN("unknown");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus: " + value);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return value;
    }
}

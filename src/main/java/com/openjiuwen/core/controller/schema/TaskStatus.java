/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

    public String getValue() {
        return value;
    }

    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

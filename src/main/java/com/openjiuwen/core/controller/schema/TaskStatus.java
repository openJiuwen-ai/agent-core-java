// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

/**
 * Task Status Enumeration.
 *
 * <p>Defines all possible states of a task.
 *
 * <p>Task Status Flow:
 * submitted -> working -> (completed | failed | paused | canceled)
 *                |
 *                -> input-required -> (continue execution or cancel)
 *
 * @author OpenJiuwen
 * @since 1.0.0
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
     * Gets the string value of the task status.
     *
     * @return the status value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the TaskStatus from its string value.
     *
     * @param value the string value
     * @return the corresponding TaskStatus
     * @throws IllegalArgumentException if value is not recognized
     */
    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus value: " + value);
    }
}


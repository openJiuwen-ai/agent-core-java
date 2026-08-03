/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Task status enumeration.
 * <p>
 * Task status flow:
 * {@code submitted -> working -> (completed | failed | paused | canceled)}
 * {@code working -> input-required -> (continue execution or cancel)}
 * <p>
 * Mirrors Python's {@code TaskStatus} in
 * {@code openjiuwen/core/controller/schema/task.py}.
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

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TaskStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.value.equals(value) || status.name().equals(value)) {
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

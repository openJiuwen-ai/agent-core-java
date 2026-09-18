/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Task execution status used by the workflow controller task model.
 *
 * @since 0.1.7
 */
public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    INTERRUPTED("interrupted"),
    INPUT_REQUIRED("input-required");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolve a persisted or wire status token.
     *
     * @param value status string from checkpoints or JSON
     * @return matching status, or {@link #PENDING} when the token is blank or unknown
     */
    @JsonCreator
    public static TaskStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (TaskStatus status : values()) {
            if (status.value.equals(value) || status.name().equals(value)) {
                return status;
            }
        }
        return PENDING;
    }
}

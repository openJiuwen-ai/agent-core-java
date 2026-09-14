/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.task;

/**
 * Lifecycle status of a single todo item.
 *
 * <p>Mirrors Python's {@code TodoStatus} in
 * {@code openjiuwen/harness/schema/task.py}.
 */
public enum TodoStatus {
    PENDING("pending", "[ ]"),
    IN_PROGRESS("in_progress", "[\u2192]"),
    COMPLETED("completed", "[\u221a]"),
    CANCELLED("cancelled", "[\u00d7]");

    private final String value;
    private final String statusIcon;

    TodoStatus(String value, String statusIcon) {
        this.value = value;
        this.statusIcon = statusIcon;
    }

    public String getValue() {
        return value;
    }

    public String getStatusIcon() {
        return statusIcon;
    }

    public static TodoStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (TodoStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}

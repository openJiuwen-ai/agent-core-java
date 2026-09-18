/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

/**
 * Task status enumeration.
 *
 * <p>Mirrors Python's {@code TaskStatus} in
 * {@code openjiuwen/core/common/task_manager/types.py}.</p>
 */
public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    TIMEOUT("timeout");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isTerminal() {
        return TaskStates.TERMINAL_STATES.contains(this);
    }
}

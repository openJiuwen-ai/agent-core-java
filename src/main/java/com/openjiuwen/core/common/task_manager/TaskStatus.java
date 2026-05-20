/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.EnumSet;
import java.util.Set;

/**
 * Coroutine-task status enumeration.
 */
public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    TIMEOUT("timeout");

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Set<TaskStatus> TERMINAL_STATES =
            EnumSet.of(COMPLETED, FAILED, CANCELLED, TIMEOUT);

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
}

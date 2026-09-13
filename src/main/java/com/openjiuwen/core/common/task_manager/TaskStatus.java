/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.EnumSet;
import java.util.Set;

/**
 * Coroutine-task status enumeration.
 * 
 * @since 0.1.7
 */
public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    TIMEOUT("timeout");

    /**
     * TERMINAL_STATES.
     * 
     * @since 0.1.7
     */
    public static final Set<TaskStatus> TERMINAL_STATES = EnumSet.of(COMPLETED, FAILED, CANCELLED, TIMEOUT);

    private final String value;

    TaskStatus(String value) {
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
}

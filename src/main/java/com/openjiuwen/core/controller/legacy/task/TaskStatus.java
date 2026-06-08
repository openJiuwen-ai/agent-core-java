/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

/**
 * Mirrors Python's {@code TaskStatus} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    INTERRUPTED
}

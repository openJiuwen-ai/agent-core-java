/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Task-related exceptions.
 * <p>
 * Mirrors Python's task exception classes from
 * <code>common/task_manager/exceptions.py</code>.
 */
public final class TaskExceptions {

    private TaskExceptions() {}

    /** Base exception for task errors. */
    public static class TaskError extends ExecutionError {
        public TaskError(StatusCode status, String msg, Object details, Throwable cause, java.util.Map<String, Object> params) {
            super(status, msg, details, cause, params);
        }
    }

    /** Raised when a task is not found. */
    public static class TaskNotFoundError extends TaskError {
        public TaskNotFoundError(String msg) {
            super(StatusCode.COMMON_TASK_NOT_FOUND, msg, null, null, null);
        }
    }

    /** Raised when a task with the same ID already exists. */
    public static class DuplicateTaskError extends TaskError {
        public DuplicateTaskError(String msg) {
            super(StatusCode.COMMON_TASK_CONFIG_ERROR, msg, null, null, null);
        }
    }
}

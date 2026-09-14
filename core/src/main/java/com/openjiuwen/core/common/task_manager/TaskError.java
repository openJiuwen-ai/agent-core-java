/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;

/**
 * Mirrors Python's {@code TaskError} in
 * {@code openjiuwen/core/common/task_manager/exceptions.py}.
 */
public class TaskError extends ExecutionError {
    public TaskError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    public TaskError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    public TaskError(StatusCode status) {
        super(status);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code TaskNotFoundError} in
 * {@code openjiuwen/core/common/task_manager/exceptions.py}.
 */
public class TaskNotFoundError extends TaskError {

    public static final StatusCode STATUS = StatusCode.COMMON_TASK_NOT_FOUND;

    public TaskNotFoundError() {
        super(STATUS);
    }

    public TaskNotFoundError(String msg) {
        this(msg, null, null, Map.of());
    }

    public TaskNotFoundError(String msg, Object details, Throwable cause, Map<String, Object> kwargs) {
        super(STATUS, msg, details, cause, kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs));
    }
}

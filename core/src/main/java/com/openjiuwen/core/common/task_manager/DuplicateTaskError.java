/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code DuplicateTaskError} in
 * {@code openjiuwen/core/common/task_manager/exceptions.py}.
 */
public class DuplicateTaskError extends TaskError {

    public static final StatusCode STATUS = StatusCode.COMMON_TASK_CONFIG_ERROR;

    public DuplicateTaskError() {
        super(STATUS);
    }

    public DuplicateTaskError(String msg) {
        this(msg, null, null, Map.of());
    }

    public DuplicateTaskError(String msg, Object details, Throwable cause, Map<String, Object> kwargs) {
        super(STATUS, msg, details, cause, kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs));
    }
}

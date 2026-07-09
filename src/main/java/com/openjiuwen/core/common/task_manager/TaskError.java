/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;

/**
 * Base exception for common task-manager errors.
 * 
 * @since 0.1.7
 */
public class TaskError extends ExecutionError {
    /**
     * TaskError.
     * 
     * @param status status
     * @param msg msg
     * @since 0.1.7
     */
    public TaskError(StatusCode status, String msg) {
        super(status, msg, null, null, Map.of("error_msg", msg));
    }
}

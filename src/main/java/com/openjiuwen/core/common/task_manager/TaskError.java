/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;

/**
 * Base exception for common task-manager errors.
 */
public class TaskError extends ExecutionError {
    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskError(StatusCode status, String msg) {
        super(status, msg, null, null, Map.of("error_msg", msg));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Raised when a task cannot be found.
 */
public class TaskNotFoundError extends TaskError {
    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskNotFoundError(String msg) {
        super(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR, msg);
    }
}

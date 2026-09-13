/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Raised when a task cannot be found.
 * 
 * @since 0.1.7
 */
public class TaskNotFoundError extends TaskError {
    /**
     * TaskNotFoundError.
     * 
     * @param msg msg
     * @since 0.1.7
     */
    public TaskNotFoundError(String msg) {
        super(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR, msg);
    }
}

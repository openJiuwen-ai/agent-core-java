/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Raised when the same task id is registered twice.
 * 
 * @since 0.1.7
 */
public class DuplicateTaskError extends TaskError {
    /**
     * DuplicateTaskError.
     * 
     * @param msg msg
     * @since 0.1.7
     */
    public DuplicateTaskError(String msg) {
        super(StatusCode.AGENT_CONTROLLER_TASK_PARAM_ERROR, msg);
    }
}

/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Infrastructure / environment / dependency failures.
 * Must abort current execution.
 */
public class FrameworkError extends BaseError {

    /**
     * Creates a FrameworkError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public FrameworkError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a FrameworkError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public FrameworkError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a FrameworkError with status only.
     *
     * @param status the status code
     */
    public FrameworkError(StatusCode status) {
        super(status);
    }

    @Override
    protected boolean defaultRecoverable() {
        return false;
    }

    @Override
    protected boolean defaultFatal() {
        return true;
    }
}

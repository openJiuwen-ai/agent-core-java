  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Session error. */
public class SessionError extends ExecutionError {
    /**
     * Creates a SessionError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public SessionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) { super(status, msg, details, cause, params); }
    /**
     * Creates a SessionError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public SessionError(StatusCode status, Map<String, Object> params) { super(status, params); }
    /**
     * Creates a SessionError with status only.
     *
     * @param status the status code
     */
    public SessionError(StatusCode status) { super(status); }
}

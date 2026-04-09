  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Non-error control-flow termination.
 * Used for normal stop, cancellation, completion, etc.
 */
public class Termination extends BaseError {
    /**
     * Creates a Termination with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public Termination(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    /**
     * Creates a Termination with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public Termination(StatusCode status, Map<String, Object> params) { super(status, params); }
    /**
     * Creates a Termination with status only.
     *
     * @param status the status code
     */
    public Termination(StatusCode status) { super(status); }

    @Override protected boolean defaultRecoverable() { return false; }
    @Override protected boolean defaultFatal() { return false; }
}

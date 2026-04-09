/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Error from an external service call. */
public class ExternalServiceError extends ExecutionError {
    /**
     * Creates an ExternalServiceError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ExternalServiceError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    /**
     * Creates an ExternalServiceError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ExternalServiceError(StatusCode status, Map<String, Object> params) { super(status, params); }
    /**
     * Creates an ExternalServiceError with status only.
     *
     * @param status the status code
     */
    public ExternalServiceError(StatusCode status) { super(status); }
}

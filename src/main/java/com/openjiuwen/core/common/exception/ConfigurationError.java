/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.exception;

import java.util.Map;

/**
 * Configuration error — a specialized {@link FrameworkError}.
 */
public class ConfigurationError extends FrameworkError {

    /**
     * Creates a ConfigurationError with full details.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param params  template parameters for message rendering
     */
    public ConfigurationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }

    /**
     * Creates a ConfigurationError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ConfigurationError(StatusCode status, Map<String, Object> params) {
        super(status, params);
    }

    /**
     * Creates a ConfigurationError with status only.
     *
     * @param status the status code
     */
    public ConfigurationError(StatusCode status) {
        super(status);
    }
}

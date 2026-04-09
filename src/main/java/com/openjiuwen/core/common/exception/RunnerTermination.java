/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import java.util.Map;

/** Runner termination — carries a reason string. */
public class RunnerTermination extends Termination {
    private final String reason;

    /**
     * Creates a RunnerTermination with reason, status, and parameters.
     *
     * @param reason the termination reason
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public RunnerTermination(String reason, StatusCode status, Map<String, Object> params) {
        super(status, params);
        this.reason = reason;
    }

    /**
     * Creates a RunnerTermination with reason and status.
     *
     * @param reason the termination reason
     * @param status the status code
     */
    public RunnerTermination(String reason, StatusCode status) {
        super(status);
        this.reason = reason;
    }

    /**
     * Gets the termination reason.
     *
     * @return the reason string
     */
    public String getReason() {
        return reason;
    }
}

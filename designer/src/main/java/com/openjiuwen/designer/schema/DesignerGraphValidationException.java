/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

/**
 * Raised when a designer graph or run document violates the wire contract.
 *
 * @since 2026-11-06
 */
public class DesignerGraphValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a validation failure with a human-readable reason.
     *
     * @param message validation reason
     */
    public DesignerGraphValidationException(String message) {
        super(message);
    }

    /**
     * Create a validation failure with a human-readable reason and cause.
     *
     * @param message validation reason
     * @param cause underlying failure
     */
    public DesignerGraphValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

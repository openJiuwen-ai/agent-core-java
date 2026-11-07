/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

/**
 * Raised by a node handler when execution cannot produce a valid result.
 *
 * @since 2026-11-06
 */
public class DesignerNodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a node failure.
     *
     * @param message failure reason
     */
    public DesignerNodeException(String message) {
        super(message);
    }

    /**
     * Create a node failure with a cause.
     *
     * @param message failure reason
     * @param cause underlying failure
     */
    public DesignerNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

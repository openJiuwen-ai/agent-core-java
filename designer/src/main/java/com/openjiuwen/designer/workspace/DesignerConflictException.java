/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

/**
 * Raised when a designer operation conflicts with current state.
 *
 * @since 2026-11-06
 */
public class DesignerConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a conflict failure.
     *
     * @param message conflict description
     */
    public DesignerConflictException(String message) {
        super(message);
    }
}

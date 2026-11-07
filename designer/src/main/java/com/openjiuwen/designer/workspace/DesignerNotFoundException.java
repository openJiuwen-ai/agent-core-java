/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

/**
 * Raised when a designer project, session, graph, or run does not exist.
 *
 * @since 2026-11-06
 */
public class DesignerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a not-found failure.
     *
     * @param message missing object description
     */
    public DesignerNotFoundException(String message) {
        super(message);
    }
}

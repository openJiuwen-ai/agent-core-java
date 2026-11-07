/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.store;

/**
 * Raised when a designer graph or run document cannot be read or written.
 *
 * @since 2026-11-06
 */
public class DesignerStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a storage failure.
     *
     * @param message failure reason
     * @param cause underlying I/O failure
     */
    public DesignerStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

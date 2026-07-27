/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

/**
 * Exception thrown when a sandbox operation fails on the jiuwenbox server.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class SandboxOperationException extends RuntimeException {
    /**
     * Constructs a SandboxOperationException with a detail message.
     *
     * @param message the detail message
     */
    public SandboxOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a SandboxOperationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public SandboxOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a SandboxOperationException with a cause.
     *
     * @param cause the underlying cause
     */
    public SandboxOperationException(Throwable cause) {
        super(cause);
    }
}

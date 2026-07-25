/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

/**
 * Exception thrown when sandbox-not-found auto-recreate retries are exhausted.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class SandboxRecreateExhaustedException extends RuntimeException {
    private final int maxRetries;
    private final String lastStaleSandboxId;

    /**
     * Constructs a SandboxRecreateExhaustedException with detail message,
     * maximum retry count, and last stale sandbox ID.
     *
     * @param message the detail message explaining the exhaustion
     * @param maxRetries the maximum number of recreate retries that were attempted
     * @param lastStaleSandboxId the sandbox ID of the last stale sandbox before exhaustion
     */
    public SandboxRecreateExhaustedException(String message, int maxRetries, String lastStaleSandboxId) {
        super(message);
        this.maxRetries = maxRetries;
        this.lastStaleSandboxId = lastStaleSandboxId;
    }

    /**
     * Returns the maximum number of recreate retries that were attempted.
     *
     * @return the maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the sandbox ID of the last stale sandbox before exhaustion.
     *
     * @return the last stale sandbox ID
     */
    public String getLastStaleSandboxId() {
        return lastStaleSandboxId;
    }
}

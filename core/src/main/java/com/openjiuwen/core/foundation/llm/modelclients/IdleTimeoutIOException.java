/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import java.io.IOException;

/**
 * Raised when the peer stays silent beyond the idle window during a body read.
 * Public so the error classification layer can distinguish idle timeouts from
 * cancellation-shaped I/O failures by type.
 *
 * @since 2026-10-19
 */
public final class IdleTimeoutIOException extends IOException {
    private static final long serialVersionUID = 1L;

    private final transient double idleSeconds;

    /**
     * Creates the timeout with the effective idle window.
     *
     * @param idleSeconds effective idle window in seconds
     */
    public IdleTimeoutIOException(double idleSeconds) {
        super("model response idle timeout after " + idleSeconds + "s of peer silence");
        this.idleSeconds = idleSeconds;
    }

    /**
     * The effective idle window that fired.
     *
     * @return idle window in seconds
     */
    public double idleSeconds() {
        return idleSeconds;
    }
}

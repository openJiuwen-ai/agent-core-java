/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

/**
 * Timeout phase of a model HTTP call, covering connect, first-segment wait, and streaming.
 *
 * <p>The response-headers-arrived point belongs to {@link #BEFORE_FIRST_SEGMENT}: the window
 * between headers and the first data segment is still "before first segment".</p>
 *
 * @since 2026-10-09
 */
public enum TimeoutPhase {
    /**
     * Request sent but TCP/TLS connection not yet established.
     */
    BEFORE_CONNECT,

    /**
     * Connection established but first data segment not yet received, including the
     * response-header wait window.
     */
    BEFORE_FIRST_SEGMENT,

    /**
     * First data segment already received.
     */
    AFTER_FIRST_SEGMENT
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

/**
 * Context-engine callback event names.
 * <p>
 * Mirrors Python's {@code ContextEvents}.
 * 
 * @since 0.1.7
 */
public final class ContextEvents {
    /**
     * CONTEXT_UPDATED.
     * 
     * @since 0.1.7
     */
    public static final String CONTEXT_UPDATED = "context_updated";

    /**
     * CONTEXT_RETRIEVED.
     * 
     * @since 0.1.7
     */
    public static final String CONTEXT_RETRIEVED = "context_retrieved";

    /**
     * CONTEXT_CLEARED.
     * 
     * @since 0.1.7
     */
    public static final String CONTEXT_CLEARED = "context_cleared";

    /**
     * CONTEXT_COMPRESSION_STATE.
     * 
     * @since 0.1.7
     */
    public static final String CONTEXT_COMPRESSION_STATE = "context_compression_state";

    /**
     * ContextEvents.
     * 
     * @since 0.1.7
     */
    private ContextEvents() {
    }
}

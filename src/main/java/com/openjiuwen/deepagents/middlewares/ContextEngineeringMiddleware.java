/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.middlewares;

/**
 * Deprecated compatibility placeholder for the removed deepagents middleware.
 *
 * <p>Python `0.1.12` moved active behavior into {@code openjiuwen.harness}.
 */
@Deprecated(forRemoval = false)
public class ContextEngineeringMiddleware {

    /**
     * Creates a new ContextEngineeringMiddleware instance.
     */
    public ContextEngineeringMiddleware() {
        // Placeholder constructor
    }

    /**
     * Processes the context for the given input.
     *
     * @param context the context to process
     * @return the processed context (placeholder)
     */
    public Object process(Object context) {
        throw new UnsupportedOperationException(
                "ContextEngineeringMiddleware is deprecated. Migrate to com.openjiuwen.harness rails.");
    }
}

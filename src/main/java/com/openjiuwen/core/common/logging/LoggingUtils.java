/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging;

/**
 * Logging utility functions.
 * <p>
 * Uses {@link ThreadLocal} (or {@link InheritableThreadLocal}) to maintain trace/session IDs
 * across threads — the Java equivalent of Python's {@code contextvars.ContextVar}.
 */
public final class LoggingUtils {

    private static final String DEFAULT_TRACE_ID = "default_trace_id";

    /**
     * InheritableThreadLocal so that child threads (virtual-thread or platform-thread)
     * inherit the parent's trace ID automatically.
     */
    private static final InheritableThreadLocal<String> TRACE_ID_CONTEXT =
        new InheritableThreadLocal<>() {
            @Override
            protected String initialValue() {
                return DEFAULT_TRACE_ID;
            }
        };

    private LoggingUtils() {
    }

    /** Set trace / session ID in current thread context. */
    public static void setSessionId(String traceId) {
        TRACE_ID_CONTEXT.set(traceId != null ? traceId : DEFAULT_TRACE_ID);
    }

    /** Get trace / session ID from current thread context. */
    public static String getSessionId() {
        String id = TRACE_ID_CONTEXT.get();
        return id != null ? id : DEFAULT_TRACE_ID;
    }

    /** Clear the current thread's trace ID (useful for thread-pool cleanup). */
    public static void clearSessionId() {
        TRACE_ID_CONTEXT.remove();
    }

    /**
     * Parse and validate max_bytes config value.
     *
     * @param maxBytesConfig raw config value
     * @return validated max bytes (capped at 100 MB)
     * @throws IllegalArgumentException if the value is not a valid integer
     */
    public static int getLogMaxBytes(Object maxBytesConfig) {
        int maxBytes;
        try {
            maxBytes = Integer.parseInt(String.valueOf(maxBytesConfig));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid max_bytes configuration: " + maxBytesConfig, e);
        }
        int defaultLogMaxBytes = 100 * 1024 * 1024; // 100 MB
        if (maxBytes <= 0 || maxBytes > defaultLogMaxBytes) {
            maxBytes = defaultLogMaxBytes;
        }
        return maxBytes;
    }
}

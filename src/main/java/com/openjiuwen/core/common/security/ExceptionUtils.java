/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

/**
 * Exception formatting utilities.
 * 
 * @since 0.1.7
 */
public final class ExceptionUtils {
    /**
     * ExceptionUtils.
     * 
     * @since 0.1.7
     */
    private ExceptionUtils() {
    }

    /**
     * Format a validation exception's errors into a human-readable multi-line string.
     * <p>
     * Generic replacement for Python's Pydantic ValidationError formatting.
     * 
     * @param t the exception
     * @return formatted string
     * @since 0.1.7
     */
    public static String formatValidationError(Throwable t) {
        if (t == null) {
            return "";
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    /**
     * Get the root cause of an exception chain.
     * 
     * @param t t
     * @return the result
     * @since 0.1.7
     */
    public static Throwable getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}

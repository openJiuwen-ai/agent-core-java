/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight circuit breaker for LLM HTTP clients.
 * <p>
 * After {@code failureThreshold} consecutive connect-level failures, subsequent
 * calls fail fast for {@code openDurationMillis} so a refused LLM backend does not
 * keep flooding the process with doomed connection attempts.
 *
 * @since 0.1.14
 */
public final class ModelCircuitBreaker {
    private static final String FAILURE_THRESHOLD_PROPERTY = "openjiuwen.llm.circuit.failure-threshold";
    private static final String OPEN_DURATION_MS_PROPERTY = "openjiuwen.llm.circuit.open-duration-millis";

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_OPEN_DURATION_MILLIS = 30_000L;

    private final int failureThreshold;
    private final long openDurationMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilMillis = new AtomicLong();

    /**
     * Create a breaker with defaults (or system-property overrides).
     *
     * @since 0.1.14
     */
    public ModelCircuitBreaker() {
        this(resolvePositiveInt(FAILURE_THRESHOLD_PROPERTY, DEFAULT_FAILURE_THRESHOLD),
                resolvePositiveLong(OPEN_DURATION_MS_PROPERTY, DEFAULT_OPEN_DURATION_MILLIS));
    }

    /**
     * Create a breaker with explicit thresholds.
     *
     * @param failureThreshold consecutive connect failures before opening
     * @param openDurationMillis how long to fail-fast while open
     * @since 0.1.14
     */
    public ModelCircuitBreaker(int failureThreshold, long openDurationMillis) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMillis = Math.max(1L, openDurationMillis);
    }

    /**
     * Fail fast when the circuit is currently open.
     *
     * @throws IOException when the breaker is open
     * @since 0.1.14
     */
    public void beforeCall() throws IOException {
        long until = openUntilMillis.get();
        long now = System.currentTimeMillis();
        if (until <= now) {
            return;
        }
        throw new IOException("LLM circuit breaker open; retry after " + (until - now) + "ms");
    }

    /**
     * Record a successful LLM call.
     *
     * @since 0.1.14
     */
    public void onSuccess() {
        consecutiveFailures.set(0);
        openUntilMillis.set(0L);
    }

    /**
     * Record a failed LLM call. Only connect-level failures trip the breaker.
     *
     * @param error failure cause
     * @since 0.1.14
     */
    public void onFailure(Throwable error) {
        if (!isConnectFailure(error)) {
            return;
        }
        int count = consecutiveFailures.incrementAndGet();
        if (count >= failureThreshold) {
            openUntilMillis.set(System.currentTimeMillis() + openDurationMillis);
        }
    }

    /**
     * Visible for tests.
     *
     * @return true when fail-fast window is active
     * @since 0.1.14
     */
    boolean isOpen() {
        return openUntilMillis.get() > System.currentTimeMillis();
    }

    /**
     * Visible for tests.
     *
     * @return consecutive connect failures
     * @since 0.1.14
     */
    int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Whether the throwable (or any cause) looks like a TCP/connect-level failure.
     *
     * @param error failure cause chain root
     * @return {@code true} for connect-level problems that should trip the breaker
     * @since 0.1.14
     */
    public static boolean isConnectFailure(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof ConnectException || cursor instanceof UnknownHostException
                    || cursor instanceof NoRouteToHostException) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("failed to connect") || lower.contains("connection refused")
                        || lower.contains("circuit breaker open")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Resolve a positive int from a system property, falling back to {@code defaultValue}.
     *
     * @param propertyName system property key
     * @param defaultValue fallback when missing or invalid
     * @return parsed positive int, or {@code defaultValue}
     */
    private static int resolvePositiveInt(String propertyName, int defaultValue) {
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            // Invalid override — keep the hard-coded default.
            return defaultValue;
        }
    }

    /**
     * Resolve a positive long from a system property, falling back to {@code defaultValue}.
     *
     * @param propertyName system property key
     * @param defaultValue fallback when missing or invalid
     * @return parsed positive long, or {@code defaultValue}
     */
    private static long resolvePositiveLong(String propertyName, long defaultValue) {
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            // Invalid override — keep the hard-coded default.
            return defaultValue;
        }
    }
}

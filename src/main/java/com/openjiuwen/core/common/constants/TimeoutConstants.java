/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

import com.openjiuwen.core.common.logging.Loggers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unified blocking-operation timeout configuration.
 *
 * <p>Blocking call sites ({@code take}/{@code get}/{@code await}/{@code join}) should fall
 * back to these values when the caller does not supply an explicit timeout, so a stuck
 * producer, worker, or child process cannot hang an agent round indefinitely.</p>
 *
 * <p>Each default can be overridden via system property, for example
 * {@code -Dopenjiuwen.timeout.blocking-queue-ms=60000}. Values are parsed once at class
 * initialization; invalid values fall back to the built-in default with a
 * {@link Loggers#PERFORMANCE} warning.</p>
 *
 * @since 0.1.14
 */
public final class TimeoutConstants {
    /** System property for {@link #BLOCKING_QUEUE_MS}. */
    public static final String PROP_BLOCKING_QUEUE_MS = "openjiuwen.timeout.blocking-queue-ms";

    /** System property for {@link #FUTURE_MS}. */
    public static final String PROP_FUTURE_MS = "openjiuwen.timeout.future-ms";

    /** System property for {@link #LATCH_MS}. */
    public static final String PROP_LATCH_MS = "openjiuwen.timeout.latch-ms";

    /** System property for {@link #processJoinMs()}. */
    public static final String PROP_PROCESS_JOIN_MS = "openjiuwen.timeout.process-join-ms";

    /** Built-in default for blocking-queue waits, in milliseconds. */
    public static final long DEFAULT_BLOCKING_QUEUE_MS = 60_000L;

    /** Built-in default for future waits, in milliseconds. */
    public static final long DEFAULT_FUTURE_MS = 300_000L;

    /** Built-in default for latch waits, in milliseconds. */
    public static final long DEFAULT_LATCH_MS = 30_000L;

    /** Built-in default for process-join waits, in milliseconds. */
    public static final long DEFAULT_PROCESS_JOIN_MS = 600_000L;

    /** Effective blocking-queue timeout after system-property override. */
    public static final long BLOCKING_QUEUE_MS = resolveLong(
            PROP_BLOCKING_QUEUE_MS, DEFAULT_BLOCKING_QUEUE_MS);

    /** Effective future timeout after system-property override. */
    public static final long FUTURE_MS = resolveLong(PROP_FUTURE_MS, DEFAULT_FUTURE_MS);

    /** Effective latch timeout after system-property override. */
    public static final long LATCH_MS = resolveLong(PROP_LATCH_MS, DEFAULT_LATCH_MS);

    static final long PROCESS_JOIN_MS = resolveLong(PROP_PROCESS_JOIN_MS, DEFAULT_PROCESS_JOIN_MS);

    private TimeoutConstants() {
    }

    /**
     * Returns the effective process-join timeout.
     *
     * @return timeout in milliseconds
     */
    public static long processJoinMs() {
        return PROCESS_JOIN_MS;
    }

    /**
     * Convert a caller timeout in seconds to milliseconds, or use {@code defaultMs}.
     *
     * @param callerTimeoutSeconds caller timeout in seconds; {@code null} or non-positive uses the default
     * @param defaultMs fallback timeout in milliseconds
     * @return timeout in milliseconds
     */
    public static long resolveCallerMs(Double callerTimeoutSeconds, long defaultMs) {
        if (callerTimeoutSeconds == null || callerTimeoutSeconds <= 0) {
            return defaultMs;
        }
        return BigDecimal.valueOf(callerTimeoutSeconds)
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * Convert a caller timeout in milliseconds, or use {@code defaultMs}.
     *
     * @param callerTimeoutMs caller timeout in milliseconds; {@code null} or non-positive uses the default
     * @param defaultMs fallback timeout in milliseconds
     * @return timeout in milliseconds
     */
    public static long resolveCallerMs(Long callerTimeoutMs, long defaultMs) {
        if (callerTimeoutMs == null || callerTimeoutMs <= 0) {
            return defaultMs;
        }
        return callerTimeoutMs;
    }

    /**
     * Resolve a future timeout from an optional caller timeout in seconds.
     *
     * @param callerTimeoutSeconds caller timeout in seconds; {@code null} or non-positive uses {@link #FUTURE_MS}
     * @return timeout in milliseconds
     */
    public static long futureMs(Double callerTimeoutSeconds) {
        return resolveCallerMs(callerTimeoutSeconds, FUTURE_MS);
    }

    private static long resolveLong(String key, long defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                warn(key, raw, "must be positive; using default " + defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            warn(key, raw, "not a number; using default " + defaultValue);
            return defaultValue;
        }
    }

    private static void warn(String key, String raw, String reason) {
        try {
            Loggers.PERFORMANCE.warning("Invalid timeout property [{}={}]: {}", key, raw, reason);
        } catch (RuntimeException ignored) {
            // Logger init during early class-load must not break timeout resolution.
        }
    }
}

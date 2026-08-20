/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

import com.openjiuwen.core.common.logging.Loggers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unified blocking-operation timeout configuration for the openJiuwen framework.
 * <p>
 * Issue #70 dimension IV — hot-path blocking &amp; async governance. All blocking call sites
 * (take/get/await/join) should default to these values when no explicit timeout is supplied by
 * the caller, so that a stuck producer / worker / child process cannot hang an entire agent
 * round indefinitely.
 * </p>
 * <p>
 * Each default can be overridden via system property (e.g.
 * {@code -Dopenjiuwen.timeout.blocking-queue-ms=60000}) for ops / tuning without code changes.
 * Values are parsed once at class-init and cached; invalid values fall back to the built-in
 * default with a {@link Loggers#PERFORMANCE} warning.
 * </p>
 *
 * @since 0.1.15
 */
public final class TimeoutConstants {
    /**
     * System property key for overriding {@link #BLOCKING_QUEUE_MS}.
     *
     * @since 0.1.15
     */
    public static final String PROP_BLOCKING_QUEUE_MS = "openjiuwen.timeout.blocking-queue-ms";

    /**
     * System property key for overriding {@link #FUTURE_MS}.
     *
     * @since 0.1.15
     */
    public static final String PROP_FUTURE_MS = "openjiuwen.timeout.future-ms";

    /**
     * System property key for overriding {@link #LATCH_MS}.
     *
     * @since 0.1.15
     */
    public static final String PROP_LATCH_MS = "openjiuwen.timeout.latch-ms";

    /**
     * System property key for overriding {@link #PROCESS_JOIN_MS}.
     *
     * @since 0.1.15
     */
    public static final String PROP_PROCESS_JOIN_MS = "openjiuwen.timeout.process-join-ms";

    /**
     * Built-in default for {@link #BLOCKING_QUEUE_MS} (60 seconds), used when the
     * system property is absent or invalid.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_BLOCKING_QUEUE_MS = 60_000L;

    /**
     * Built-in default for {@link #FUTURE_MS} (5 minutes), used when the
     * system property is absent or invalid.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_FUTURE_MS = 300_000L;

    /**
     * Built-in default for {@link #LATCH_MS} (30 seconds), used when the
     * system property is absent or invalid.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_LATCH_MS = 30_000L;

    /**
     * Built-in default for {@link #PROCESS_JOIN_MS} (10 minutes), used when the
     * system property is absent or invalid.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_PROCESS_JOIN_MS = 600_000L;

    /**
     * Effective blocking-queue poll timeout in milliseconds. Measures how long a
     * queue may stay idle (no new data) before the caller breaks out, rather than
     * total duration. Used by {@code StreamProcessor} main-loop / iterator poll
     * and {@code TaskManager} queue poll. Defaults to {@link #DEFAULT_BLOCKING_QUEUE_MS};
     * override with {@code -Dopenjiuwen.timeout.blocking-queue-ms=...}.
     *
     * @since 0.1.15
     */
    public static final long BLOCKING_QUEUE_MS = resolveLong(
            PROP_BLOCKING_QUEUE_MS, DEFAULT_BLOCKING_QUEUE_MS);

    /**
     * Effective Future.get / CountDownLatch total-wait timeout in milliseconds.
     * Caps the overall wait for a stream round or task execution. Used by
     * {@code Vertex.awaitStreamInAbilities} ({@code streamDone.get()}),
     * {@code Vertex.runExecutable} ({@code future.get()}), {@code Task.waitFor},
     * and {@code Workflow} execution-future wait. Defaults to
     * {@link #DEFAULT_FUTURE_MS}; override with
     * {@code -Dopenjiuwen.timeout.future-ms=...}.
     *
     * @since 0.1.15
     */
    public static final long FUTURE_MS = resolveLong(PROP_FUTURE_MS, DEFAULT_FUTURE_MS);

    /**
     * Effective CountDownLatch await timeout in milliseconds, for the stream
     * startup phase where upstream abilities must register before dispatch.
     * Used by {@code Vertex} ability-latch await. Defaults to
     * {@link #DEFAULT_LATCH_MS}; override with
     * {@code -Dopenjiuwen.timeout.latch-ms=...}.
     *
     * @since 0.1.15
     */
    public static final long LATCH_MS = resolveLong(PROP_LATCH_MS, DEFAULT_LATCH_MS);

    static final long PROCESS_JOIN_MS = resolveLong(
            PROP_PROCESS_JOIN_MS, DEFAULT_PROCESS_JOIN_MS);

    private TimeoutConstants() {
        // Utility class — no instantiation
    }

    /**
     * Expose process-join timeout for non-{@code constants} package callers (BashTool etc.
     * live in a different package). Kept package-private above, with a public accessor here.
     *
     * @return the effective child process join timeout in milliseconds
     * @since 0.1.15
     */
    public static long processJoinMs() {
        return PROCESS_JOIN_MS;
    }

    /**
     * Resolve a long system property, falling back to {@code defaultValue} on parse failure or
     * non-positive input. A single warning is emitted to {@link Loggers#PERFORMANCE} on
     * fallback so misconfigurations surface without silently swallowing.
     *
     * @param key the system property key
     * @param defaultValue the built-in default
     * @return the resolved value
     * @since 0.1.15
     */
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
        } catch (NumberFormatException e) {
            warn(key, raw, "not a number; using default " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Emit a single-line warning to the PERFORMANCE logger.
     *
     * @param key the property key
     * @param raw the raw value that failed
     * @param reason human-readable reason
     * @since 0.1.15
     */
    private static void warn(String key, String raw, String reason) {
        try {
            Loggers.PERFORMANCE.warning(
                    "Invalid timeout property [{}={}]: {}", key, raw, reason);
        } catch (RuntimeException ignored) {
            // Logger init order in early class-load must not break timeout resolution.
        }
    }

    /**
     * Convert a nullable caller-supplied timeout-in-seconds to milliseconds, falling back to
     * the supplied default constant when the caller did not specify one (null / non-positive).
     * Centralizes the "caller didn't pass timeout → use framework default" pattern used across
     * issue #70 dimension IV call sites.
     *
     * @param callerTimeoutSeconds caller-supplied timeout in seconds (may be null / non-positive)
     * @param defaultMs the framework default in milliseconds
     * @return the resolved timeout in milliseconds
     * @since 0.1.15
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
     * Convert a nullable caller-supplied timeout-in-millis to the effective value, falling back
     * to the supplied default constant when the caller did not specify one (null / non-positive).
     *
     * @param callerTimeoutMs caller-supplied timeout in milliseconds (may be null / non-positive)
     * @param defaultMs the framework default in milliseconds
     * @return the resolved timeout in milliseconds
     * @since 0.1.15
     */
    public static long resolveCallerMs(Long callerTimeoutMs, long defaultMs) {
        if (callerTimeoutMs == null || callerTimeoutMs <= 0) {
            return defaultMs;
        }
        return callerTimeoutMs;
    }

    /**
     * Resolve a Future.get timeout as a (value, unit) pair for use with
     * {@code future.get(timeout, unit)} call sites.
     *
     * @param callerTimeoutSeconds caller-supplied timeout in seconds (may be null / non-positive)
     * @return the resolved timeout in milliseconds (always falls back to {@link #FUTURE_MS})
     * @since 0.1.15
     */
    public static long futureMs(Double callerTimeoutSeconds) {
        return resolveCallerMs(callerTimeoutSeconds, FUTURE_MS);
    }
}

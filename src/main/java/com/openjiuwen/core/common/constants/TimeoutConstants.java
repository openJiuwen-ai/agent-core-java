/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.constants;

import java.util.concurrent.TimeUnit;

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
     * TimeoutConstants.
     *
     * @since 0.1.15
     */
    private TimeoutConstants() {
        // Utility class — no instantiation
    }

    // ======================== System property keys ========================

    /**
     * Property key for blocking-queue poll timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final String PROP_BLOCKING_QUEUE_MS = "openjiuwen.timeout.blocking-queue-ms";

    /**
     * Property key for Future.get timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final String PROP_FUTURE_MS = "openjiuwen.timeout.future-ms";

    /**
     * Property key for latch / await timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final String PROP_LATCH_MS = "openjiuwen.timeout.latch-ms";

    /**
     * Property key for child process join timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final String PROP_PROCESS_JOIN_MS = "openjiuwen.timeout.process-join-ms";

    // ======================== Built-in defaults ========================

    /**
     * Default blocking-queue poll timeout: 30 seconds. Used when a caller does not pass an
     * explicit timeout to {@code queue.poll(...)} / {@code queue.take()} sites such as
     * {@code TaskManager.asCompleted} and {@code StreamProcessor} main loop.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_BLOCKING_QUEUE_MS = 30_000L;

    /**
     * Default Future.get timeout: 5 minutes. Used at {@code Task.waitFor},
     * {@code Workflow.waitForExecution}, {@code Vertex.streamDone.get()} / allOf().get() sites.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_FUTURE_MS = 300_000L;

    /**
     * Default latch await timeout: 30 seconds. Used at {@code Vertex.abilityLatch.await()} and
     * similar count-down-latch wait sites.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_LATCH_MS = 30_000L;

    /**
     * Default child process join timeout: 10 minutes. Used by BashTool / CodeTool /
     * PowerShellTool when waiting on {@code process.onExit().join()}.
     *
     * @since 0.1.15
     */
    public static final long DEFAULT_PROCESS_JOIN_MS = 600_000L;

    // ======================== Resolved (cached) values ========================

    /**
     * Effective blocking-queue poll timeout in milliseconds, resolved from system property
     * {@link #PROP_BLOCKING_QUEUE_MS} or the built-in default.
     *
     * @since 0.1.15
     */
    public static final long BLOCKING_QUEUE_MS = resolveLong(
            PROP_BLOCKING_QUEUE_MS, DEFAULT_BLOCKING_QUEUE_MS);

    /**
     * Effective Future.get timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final long FUTURE_MS = resolveLong(PROP_FUTURE_MS, DEFAULT_FUTURE_MS);

    /**
     * Effective latch await timeout in milliseconds.
     *
     * @since 0.1.15
     */
    public static final long LATCH_MS = resolveLong(PROP_LATCH_MS, DEFAULT_LATCH_MS);

    /**
     * Effective child process join timeout in milliseconds.
     *
     * @since 0.1.15
     */
    static final long PROCESS_JOIN_MS = resolveLong(
            PROP_PROCESS_JOIN_MS, DEFAULT_PROCESS_JOIN_MS);

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

    // ======================== Helpers ========================

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
            com.openjiuwen.core.common.logging.Loggers.PERFORMANCE.warning(
                    "Invalid timeout property [{}={}]: {}", key, raw, reason);
        } catch (Throwable ignored) {
            // Logger init order in early class-load must not break timeout resolution.
        }
    }

    // ======================== Convenience accessors ========================

    /**
     * Blocking-queue poll timeout as a {@link java.time.Duration} for callers that prefer it.
     *
     * @return the blocking-queue timeout duration
     * @since 0.1.15
     */
    public static java.time.Duration blockingQueueDuration() {
        return java.time.Duration.ofMillis(BLOCKING_QUEUE_MS);
    }

    /**
     * Future.get timeout as a {@link java.time.Duration}.
     *
     * @return the future-get timeout duration
     * @since 0.1.15
     */
    public static java.time.Duration futureDuration() {
        return java.time.Duration.ofMillis(FUTURE_MS);
    }

    /**
     * Latch await timeout as a {@link java.time.Duration}.
     *
     * @return the latch timeout duration
     * @since 0.1.15
     */
    public static java.time.Duration latchDuration() {
        return java.time.Duration.ofMillis(LATCH_MS);
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
        return Math.round(callerTimeoutSeconds * 1000.0);
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

    /**
     * Convenience: blocking-queue poll timeout in the requested time unit.
     *
     * @param unit the time unit for the return value
     * @return the blocking-queue timeout in the requested unit
     * @since 0.1.15
     */
    public static long blockingQueue(TimeUnit unit) {
        return unit.convert(BLOCKING_QUEUE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Convenience: Future.get timeout in the requested time unit.
     *
     * @param unit the time unit for the return value
     * @return the future timeout in the requested unit
     * @since 0.1.15
     */
    public static long future(TimeUnit unit) {
        return unit.convert(FUTURE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Convenience: latch await timeout in the requested time unit.
     *
     * @param unit the time unit for the return value
     * @return the latch timeout in the requested unit
     * @since 0.1.15
     */
    public static long latch(TimeUnit unit) {
        return unit.convert(LATCH_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Convenience: child process join timeout in the requested time unit.
     *
     * @param unit the time unit for the return value
     * @return the process-join timeout in the requested unit
     * @since 0.1.15
     */
    public static long processJoin(TimeUnit unit) {
        return unit.convert(PROCESS_JOIN_MS, TimeUnit.MILLISECONDS);
    }
}

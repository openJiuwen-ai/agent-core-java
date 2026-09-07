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
 */
public final class TimeoutConstants {
    public static final String PROP_BLOCKING_QUEUE_MS = "openjiuwen.timeout.blocking-queue-ms";
    public static final String PROP_FUTURE_MS = "openjiuwen.timeout.future-ms";
    public static final String PROP_LATCH_MS = "openjiuwen.timeout.latch-ms";
    public static final String PROP_PROCESS_JOIN_MS = "openjiuwen.timeout.process-join-ms";

    public static final long DEFAULT_BLOCKING_QUEUE_MS = 60_000L;
    public static final long DEFAULT_FUTURE_MS = 300_000L;
    public static final long DEFAULT_LATCH_MS = 30_000L;
    public static final long DEFAULT_PROCESS_JOIN_MS = 600_000L;

    public static final long BLOCKING_QUEUE_MS = resolveLong(
            PROP_BLOCKING_QUEUE_MS, DEFAULT_BLOCKING_QUEUE_MS);
    public static final long FUTURE_MS = resolveLong(PROP_FUTURE_MS, DEFAULT_FUTURE_MS);
    public static final long LATCH_MS = resolveLong(PROP_LATCH_MS, DEFAULT_LATCH_MS);
    static final long PROCESS_JOIN_MS = resolveLong(PROP_PROCESS_JOIN_MS, DEFAULT_PROCESS_JOIN_MS);

    private TimeoutConstants() {
    }

    public static long processJoinMs() {
        return PROCESS_JOIN_MS;
    }

    public static long resolveCallerMs(Double callerTimeoutSeconds, long defaultMs) {
        if (callerTimeoutSeconds == null || callerTimeoutSeconds <= 0) {
            return defaultMs;
        }
        return BigDecimal.valueOf(callerTimeoutSeconds)
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    public static long resolveCallerMs(Long callerTimeoutMs, long defaultMs) {
        if (callerTimeoutMs == null || callerTimeoutMs <= 0) {
            return defaultMs;
        }
        return callerTimeoutMs;
    }

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

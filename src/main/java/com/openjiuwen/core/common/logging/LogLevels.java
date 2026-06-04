/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.loguru.LoguruConfigProvider;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Logging level normalization helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.common.logging.log_levels}.</p>
 */
public final class LogLevels {

    public static final int CRITICAL = 50;
    public static final int FATAL = CRITICAL;
    public static final int ERROR = 40;
    public static final int WARNING = 30;
    public static final int WARN = WARNING;
    public static final int INFO = 20;
    public static final int DEBUG = 10;
    public static final int NOTSET = 0;

    public static final Map<String, Integer> NAME_TO_LEVEL = Map.of(
        "CRITICAL", CRITICAL,
        "FATAL", FATAL,
        "ERROR", ERROR,
        "WARNING", WARNING,
        "WARN", WARN,
        "INFO", INFO,
        "DEBUG", DEBUG,
        "NOTSET", NOTSET
    );

    private LogLevels() {
    }

    /**
     * Normalize a log level name/value to the integer logging level.
     */
    public static int normalizeLogLevel(Object level) {
        return normalizeLogLevel(level, WARNING);
    }

    /**
     * Normalize a log level name/value to the integer logging level.
     */
    public static int normalizeLogLevel(Object level, int defaultLevel) {
        if (level instanceof Boolean) {
            return defaultLevel;
        }
        if (level instanceof Number number) {
            return number.intValue();
        }
        if (level instanceof String text) {
            return NAME_TO_LEVEL.getOrDefault(text.toUpperCase(Locale.ROOT), defaultLevel);
        }
        return defaultLevel;
    }

    /**
     * Extract and normalize the backend name from a logging config map.
     */
    public static String extractBackend(Map<String, Object> loggingConfig) {
        Object backend = loggingConfig != null ? loggingConfig.getOrDefault("backend", "default") : "default";
        if (!(backend instanceof String backendName) || backendName.isBlank()) {
            return "default";
        }
        return backendName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalize a logging config section by dispatching to the selected backend provider.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeLoggingConfig(Object loggingConfig) {
        return normalizeLoggingConfig(loggingConfig, WARNING);
    }

    /**
     * Normalize a logging config section by dispatching to the selected backend provider.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeLoggingConfig(Object loggingConfig, int defaultLevel) {
        if (!(loggingConfig instanceof Map<?, ?> rawMap)) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("level", defaultLevel);
            return fallback;
        }

        Map<String, Object> normalizedConfig = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalizedConfig.put(String.valueOf(key), value));
        normalizedConfig.put("level", normalizeLogLevel(normalizedConfig.getOrDefault("level", defaultLevel),
            defaultLevel));

        String backend = extractBackend(normalizedConfig);
        if ("loguru".equals(backend)) {
            return LoguruConfigProvider.normalizeLoguruLoggingConfig(normalizedConfig, INFO);
        }
        return normalizedConfig;
    }
}

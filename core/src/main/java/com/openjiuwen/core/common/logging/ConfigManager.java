/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.Map;

/**
 * Backward-compatibility shim for logging level helpers.
 *
 * <p>Mirrors Python's re-exports in
 * {@code openjiuwen/core/common/logging/config_manager.py}.</p>
 */
public final class ConfigManager {

    public static final int CRITICAL = LogLevels.CRITICAL;
    public static final int FATAL = LogLevels.FATAL;
    public static final int ERROR = LogLevels.ERROR;
    public static final int WARNING = LogLevels.WARNING;
    public static final int WARN = LogLevels.WARN;
    public static final int INFO = LogLevels.INFO;
    public static final int DEBUG = LogLevels.DEBUG;
    public static final int NOTSET = LogLevels.NOTSET;
    public static final Map<String, Integer> NAME_TO_LEVEL = LogLevels.NAME_TO_LEVEL;

    private ConfigManager() {
    }

    public static int normalizeLogLevel(Object level) {
        return LogLevels.normalizeLogLevel(level);
    }

    public static int normalizeLogLevel(Object level, int defaultLevel) {
        return LogLevels.normalizeLogLevel(level, defaultLevel);
    }

    public static String extractBackend(Map<String, Object> loggingConfig) {
        return LogLevels.extractBackend(loggingConfig);
    }

    public static Map<String, Object> normalizeLoggingConfig(Object loggingConfig) {
        return LogLevels.normalizeLoggingConfig(loggingConfig);
    }

    public static Map<String, Object> normalizeLoggingConfig(Object loggingConfig, int defaultLevel) {
        return LogLevels.normalizeLoggingConfig(loggingConfig, defaultLevel);
    }
}

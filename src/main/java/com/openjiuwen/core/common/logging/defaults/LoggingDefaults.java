/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.logging.LogManager;

import java.util.Map;

/**
 * Global logging-config facade.
 *
 * <p>Mirrors Python's module-level {@code log_config}, {@code configure_log},
 * {@code configure_log_config}, and {@code get_log_config_snapshot} in
 * {@code openjiuwen/core/common/logging/log_config.py}.</p>
 */
public final class LoggingDefaults {

    private static volatile LogConfig logConfigInstance = new LogConfig();

    private LoggingDefaults() {
    }

    public static LogConfig logConfig() {
        return logConfigInstance;
    }

    public static void configureLog(String configPath) {
        logConfigInstance.reload(configPath);
        LogManager.reset();
    }

    public static void configureLogConfig(Map<String, Object> loggingConfig) {
        logConfigInstance.loadFromDict(loggingConfig);
        LogManager.reset();
    }

    public static Map<String, Object> getLogConfigSnapshot() {
        return logConfigInstance.getSnapshot();
    }

    public static synchronized void reset() {
        logConfigInstance = new LogConfig();
    }
}

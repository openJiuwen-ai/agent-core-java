/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log Manager — provides logger creation, registration, and retrieval.
 * <p>
 * Thread-safe via ConcurrentHashMap. Lazy initialization of individual loggers.
 */
public final class LogManager {

    private static final Map<String, LoggerProtocol> LOGGERS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile LoggerFactory defaultLoggerFactory;

    private LogManager() {
    }

    /** Functional interface for creating loggers from a type name and config. */
    @FunctionalInterface
    public interface LoggerFactory {
        LoggerProtocol create(String logType, Map<String, Object> config);
    }

    /** Set the default logger factory (e.g., DefaultLogger::new). */
    public static void setDefaultLoggerFactory(LoggerFactory factory) {
        defaultLoggerFactory = factory;
    }

    /**
     * Initialize the logging system. Idempotent — safe to call multiple times.
     * <p>
     * Loads default configuration and creates the standard set of loggers.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (defaultLoggerFactory == null) {
            try {
                // Attempt to load DefaultLogger via reflection to avoid hard dependency
                Class<?> cls = Class.forName(
                    "com.openjiuwen.core.common.logging.defaults.DefaultLogger");
                var ctor = cls.getConstructor(String.class, Map.class);
                defaultLoggerFactory = (logType, config) -> {
                    try {
                        return (LoggerProtocol) ctor.newInstance(logType, config);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create DefaultLogger", e);
                    }
                };
            } catch (Exception e) {
                throw new RuntimeException("No default logger factory set and cannot load DefaultLogger", e);
            }
        }

        // Create standard loggers from default config
        var logConfig = LogConfigProvider.getLogConfig();
        if (logConfig != null) {
            logConfig.forEach((logType, config) -> {
                if (!LOGGERS.containsKey(logType)) {
                    LOGGERS.put(logType, defaultLoggerFactory.create(logType, config));
                }
            });
        }

        initialized = true;
    }

    /** Register a custom logger for a given log type. */
    public static void registerLogger(String logType, LoggerProtocol logger) {
        LOGGERS.put(logType, logger);
    }

    /**
     * Get a logger by type. Creates one on-demand if not present.
     */
    public static LoggerProtocol getLogger(String logType) {
        if (!initialized) {
            initialize();
        }
        return LOGGERS.computeIfAbsent(logType, type -> {
            var logConfig = LogConfigProvider.getLogConfig();
            Map<String, Object> config = logConfig != null ? logConfig.get(type) : null;
            if (config == null) {
                config = Map.of("level", "INFO", "output", "console");
            }
            return defaultLoggerFactory.create(type, config);
        });
    }

    /** Get all registered loggers. */
    public static Map<String, LoggerProtocol> getAllLoggers() {
        if (!initialized) {
            initialize();
        }
        return Map.copyOf(LOGGERS);
    }

    /** Reset the log manager — primarily for testing. */
    public static synchronized void reset() {
        LOGGERS.clear();
        initialized = false;
        defaultLoggerFactory = null;
    }

    // ==================== Internal Config Provider ====================

    /**
     * Simple provider interface for log configuration.
     * Override via {@link LogConfigProvider#setProvider} for custom configs.
     */
    public static final class LogConfigProvider {
        private static volatile java.util.function.Supplier<Map<String, Map<String, Object>>> provider;

        private LogConfigProvider() {
        }

        public static void setProvider(java.util.function.Supplier<Map<String, Map<String, Object>>> p) {
            provider = p;
        }

        static Map<String, Map<String, Object>> getLogConfig() {
            return provider != null ? provider.get() : null;
        }
    }
}

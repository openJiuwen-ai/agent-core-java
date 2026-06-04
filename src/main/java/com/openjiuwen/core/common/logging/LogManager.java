/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log Manager — provides logger creation, registration, and retrieval.
 *
 * <p>Mirrors Python's {@code LogManager} in {@code openjiuwen.core.common.logging.manager}.</p>
 */
public final class LogManager {

    private static final Map<String, LoggerProtocol> LOGGERS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile LoggerFactory defaultLoggerFactory;
    private static volatile String defaultLoggerFactoryBackend;
    private static volatile String selectedBackend;

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
        defaultLoggerFactoryBackend = null;
    }

    /**
     * Backward-compatible alias for Python's {@code set_default_logger_class}.
     */
    public static void setDefaultLoggerClass(LoggerFactory factory) {
        setDefaultLoggerFactory(factory);
    }

    /**
     * Initialize with the configured backend.
     */
    public static synchronized void initialize() {
        initialize(null);
    }

    /**
     * Initialize the logging system with an optional backend override.
     */
    public static synchronized void initialize(String backend) {
        String requestedBackend = normalizeBackend(backend);
        String resolvedBackend = requestedBackend != null ? requestedBackend : normalizeBackend(getConfiguredBackend());
        if (resolvedBackend == null) {
            resolvedBackend = "default";
        }

        if (initialized) {
            if (resolvedBackend.equals(selectedBackend)) {
                return;
            }
            closeAndClearLoggers();
            initialized = false;
        }
        selectedBackend = resolvedBackend;
        LoggerFactory factory = getDefaultLoggerFactory(resolvedBackend);

        Map<String, Map<String, Object>> logConfig = getAllConfigs(resolvedBackend);
        if (logConfig == null || logConfig.isEmpty()) {
            throw new RuntimeException(
                "LogConfig not available. Please ensure logging configuration is properly configured.");
        }
        logConfig.forEach((logType, config) -> LOGGERS.computeIfAbsent(logType, key -> factory.create(key, config)));

        initialized = true;
    }

    /** Register a custom logger for a given log type. */
    public static void registerLogger(String logType, LoggerProtocol logger) {
        if (logger == null) {
            throw new TypeError("Logger must implement LoggerProtocol, got null");
        }
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
            Map<String, Object> config = getCustomConfig(type, selectedBackend);
            return getDefaultLoggerFactory(selectedBackend != null ? selectedBackend : getConfiguredBackend())
                .create(type, config);
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
        closeAndClearLoggers();
        initialized = false;
        defaultLoggerFactory = null;
        defaultLoggerFactoryBackend = null;
        selectedBackend = null;
        LazyLogger.resetAll();
        try {
            com.openjiuwen.core.common.logging.events.EventClassRegistry.resetCommonLoggerCache();
        } catch (NoClassDefFoundError ignored) {
            // Event registry may not be loaded yet.
        }
    }

    private static LoggerFactory getDefaultLoggerFactory(String backend) {
        String resolvedBackend = normalizeBackend(backend);
        if (resolvedBackend == null) {
            resolvedBackend = "default";
        }
        if (defaultLoggerFactory != null
                && (defaultLoggerFactoryBackend == null || defaultLoggerFactoryBackend.equals(resolvedBackend))) {
            return defaultLoggerFactory;
        }
        defaultLoggerFactory = getLoggerFactoryForBackend(resolvedBackend);
        defaultLoggerFactoryBackend = resolvedBackend;
        return defaultLoggerFactory;
    }

    private static String getConfiguredBackend() {
        try {
            return LoggingDefaults.logConfig().getBackend();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LoggerFactory getLoggerFactoryForBackend(String backend) {
        String backendName = normalizeBackend(backend);
        if (backendName == null || "default".equals(backendName)) {
            return instantiateFactory("com.openjiuwen.core.common.logging.defaults.DefaultLogger");
        }
        if ("loguru".equals(backendName)) {
            return instantiateFactory("com.openjiuwen.core.common.logging.loguru.LoguruLogger");
        }
        throw new RuntimeException("Unsupported logging backend: " + backend);
    }

    private static LoggerFactory instantiateFactory(String className) {
        try {
            Class<?> cls = Class.forName(className);
            var ctor = cls.getConstructor(String.class, Map.class);
            return (logType, config) -> {
                try {
                    return (LoggerProtocol) ctor.newInstance(logType, config);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create logger " + className, e);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("No default logger factory set and cannot load " + className, e);
        }
    }

    private static Map<String, Map<String, Object>> getAllConfigs(String backend) {
        if (LogConfigProvider.provider != null) {
            return LogConfigProvider.provider.get();
        }
        return LoggingDefaults.logConfig().getAllConfigs(backend);
    }

    private static Map<String, Object> getCustomConfig(String logType, String backend) {
        if (LogConfigProvider.provider != null) {
            Map<String, Map<String, Object>> configs = LogConfigProvider.provider.get();
            Map<String, Object> config = configs != null ? configs.get(logType) : null;
            if (config != null) {
                return config;
            }
            return Map.of("level", LogLevels.INFO, "output", "console");
        }
        return LoggingDefaults.logConfig().getCustomConfig(logType, backend);
    }

    private static String normalizeBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return null;
        }
        return backend.trim().toLowerCase();
    }

    private static void closeAndClearLoggers() {
        for (LoggerProtocol logger : LOGGERS.values()) {
            closeQuietly(logger);
        }
        LOGGERS.clear();
    }

    private static void closeQuietly(LoggerProtocol logger) {
        try {
            Method close = logger.getClass().getMethod("close");
            close.invoke(logger);
        } catch (NoSuchMethodException ignored) {
            // Logger does not need closing.
        } catch (Exception ignored) {
            // Reset must be best-effort.
        }
    }

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
    }

    /**
     * Runtime type-check error equivalent for invalid logger registrations.
     */
    public static class TypeError extends RuntimeException {
        public TypeError(String message) {
            super(message);
        }
    }
}

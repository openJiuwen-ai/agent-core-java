/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LoggingUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default backend configuration normalization and validation helpers.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/core/common/logging/default/config_provider.py}.</p>
 */
public final class DefaultConfigProvider {

    private static final Map<String, LoggerBaseKeys> LOGGER_BASE_KEYS = Map.of(
        "common", new LoggerBaseKeys("log_file", "run/jiuwen.log", "output", List.of("console", "file")),
        "interface", new LoggerBaseKeys("interface_log_file", "interface/jiuwen_interface.log",
            "interface_output", List.of("console", "file")),
        "prompt_builder", new LoggerBaseKeys("prompt_builder_interface_log_file",
            "interface/jiuwen_prompt_builder_interface.log", "interface_output", List.of("console", "file")),
        "performance", new LoggerBaseKeys("performance_log_file", "performance/jiuwen_performance.log",
            "performance_output", List.of("console", "file"))
    );

    private static final Set<String> DEFAULT_ALLOWED_ROOT_KEYS = Set.of(
        "backend",
        "level",
        "structured_output_format",
        "backup_count",
        "max_bytes",
        "format",
        "log_path",
        "log_file",
        "output",
        "interface_log_file",
        "interface_output",
        "prompt_builder_interface_log_file",
        "performance_log_file",
        "performance_output",
        "log_file_pattern",
        "backup_file_pattern",
        "propagate",
        "loggers"
    );

    private static final Set<String> DEFAULT_ALLOWED_LOGGER_KEYS = Set.of("level");

    private DefaultConfigProvider() {
    }

    /**
     * Normalize a default-backend config map.
     */
    public static Map<String, Object> normalizeDefaultLoggingConfig(Object loggingConfig) {
        return normalizeDefaultLoggingConfig(loggingConfig, LogLevels.WARNING);
    }

    /**
     * Normalize a default-backend config map.
     */
    public static Map<String, Object> normalizeDefaultLoggingConfig(Object loggingConfig, int defaultLevel) {
        if (!(loggingConfig instanceof Map<?, ?> rawMap)) {
            return deepCopyMap(DefaultLogConstants.defaultInnerLogConfig());
        }

        Map<String, Object> normalizedConfig = mapFrom(rawMap);
        normalizedConfig.put("backend", "default");
        normalizedConfig.put(
            "level",
            LogLevels.normalizeLogLevel(
                normalizedConfig.getOrDefault("level",
                    DefaultLogConstants.defaultInnerLogConfig().getOrDefault("level", defaultLevel)),
                defaultLevel
            )
        );

        Object loggersConfig = normalizedConfig.get("loggers");
        if (loggersConfig == null) {
            normalizedConfig.put("loggers", new LinkedHashMap<String, Object>());
        } else if (loggersConfig instanceof Map<?, ?> loggerMap) {
            Map<String, Object> normalizedLoggers = new LinkedHashMap<>();
            loggerMap.forEach((loggerName, loggerConfig) ->
                normalizedLoggers.put(
                    String.valueOf(loggerName),
                    normalizeDefaultLoggerConfig(loggerConfig, defaultLevel)
                )
            );
            normalizedConfig.put("loggers", normalizedLoggers);
        }

        return normalizedConfig;
    }

    /**
     * Validate the default backend config shape.
     */
    public static void validateDefaultBackendConfig(Map<String, Object> loggingConfig) {
        Set<String> unknownKeys = new LinkedHashSet<>(loggingConfig.keySet());
        unknownKeys.removeAll(DEFAULT_ALLOWED_ROOT_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw ErrorHelper.buildError(
                StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg",
                "default backend config has unsupported keys: " + new ArrayList<>(unknownKeys)
            );
        }

        Object loggersConfig = loggingConfig.get("loggers");
        if (loggersConfig == null) {
            return;
        }
        if (!(loggersConfig instanceof Map<?, ?> loggerMap)) {
            throw ErrorHelper.buildError(
                StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg",
                "default backend config 'loggers' must be a mapping"
            );
        }

        loggerMap.forEach((loggerName, loggerConfig) -> {
            if (!(loggerConfig instanceof Map<?, ?> typedLogger)) {
                throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_CONFIG_INVALID,
                    "error_msg",
                    "default logger config for '" + loggerName + "' must be a mapping"
                );
            }

            Set<String> unknownLoggerKeys = new LinkedHashSet<>();
            typedLogger.forEach((key, value) -> unknownLoggerKeys.add(String.valueOf(key)));
            unknownLoggerKeys.removeAll(DEFAULT_ALLOWED_LOGGER_KEYS);
            if (!unknownLoggerKeys.isEmpty()) {
                throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_CONFIG_INVALID,
                    "error_msg",
                    "default logger '" + loggerName + "' has unsupported keys: "
                        + new ArrayList<>(unknownLoggerKeys)
                );
            }
        });
    }

    /**
     * Normalize then validate a default-backend config.
     */
    public static Map<String, Object> loadDefaultBackendConfig(Map<String, Object> loggingConfig) {
        Map<String, Object> normalizedConfig = normalizeDefaultLoggingConfig(loggingConfig);
        validateDefaultBackendConfig(normalizedConfig);
        return normalizedConfig;
    }

    /**
     * Build the materialized logger config for one log type.
     */
    public static Map<String, Object> buildDefaultLoggerConfig(Map<String, Object> loggingConfig, String logType) {
        Map<String, Object> defaultInnerConfig = DefaultLogConstants.defaultInnerLogConfig();
        String logPath = getLogPath(loggingConfig);
        LoggerBaseKeys loggerKeys = LOGGER_BASE_KEYS.get(logType);
        String defaultLogFile = loggerKeys != null ? loggerKeys.defaultLogFile : logType + ".log";
        Object defaultOutput = loggerKeys != null ? loggerKeys.defaultOutput : defaultInnerConfig.get("output");
        String logFileKey = loggerKeys != null ? loggerKeys.logFileKey : null;
        String outputKey = loggerKeys != null ? loggerKeys.outputKey : "output";

        Object configuredLogFile = logFileKey != null
            ? loggingConfig.getOrDefault(logFileKey, defaultLogFile)
            : defaultLogFile;
        Object configuredOutput = loggingConfig.getOrDefault(outputKey, defaultOutput);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", "default");
        config.put("log_file", resolveLogFile(logPath, String.valueOf(configuredLogFile)));
        config.put("output", deepCopy(configuredOutput));
        config.put(
            "level",
            LogLevels.normalizeLogLevel(
                loggingConfig.getOrDefault("level", defaultInnerConfig.getOrDefault("level", LogLevels.WARNING)),
                LogLevels.WARNING
            )
        );
        config.put(
            "structured_output_format",
            loggingConfig.getOrDefault(
                "structured_output_format",
                defaultInnerConfig.getOrDefault("structured_output_format", "json")
            )
        );
        config.put(
            "backup_count",
            loggingConfig.getOrDefault("backup_count", defaultInnerConfig.getOrDefault("backup_count", 20))
        );
        config.put(
            "max_bytes",
            loggingConfig.getOrDefault("max_bytes", defaultInnerConfig.getOrDefault("max_bytes", 20_971_520))
        );
        config.put(
            "format",
            loggingConfig.getOrDefault(
                "format",
                defaultInnerConfig.getOrDefault(
                    "format",
                    "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s"
                )
            )
        );
        config.put("log_file_pattern", loggingConfig.get("log_file_pattern"));
        config.put("backup_file_pattern", loggingConfig.get("backup_file_pattern"));

        Integer levelOverride = getLoggerLevelOverride(loggingConfig, logType);
        if (levelOverride != null) {
            config.put("level", levelOverride);
        }

        config.put("level", LogLevels.normalizeLogLevel(config.get("level"), LogLevels.WARNING));
        config.put("log_file", resolveLogFile(logPath, String.valueOf(config.get("log_file"))));
        return config;
    }

    private static Object normalizeDefaultLoggerConfig(Object loggerConfig, int defaultLevel) {
        if (!(loggerConfig instanceof Map<?, ?> rawMap)) {
            return loggerConfig;
        }

        Map<String, Object> normalizedLogger = mapFrom(rawMap);
        if (normalizedLogger.containsKey("level")) {
            normalizedLogger.put(
                "level",
                LogLevels.normalizeLogLevel(normalizedLogger.get("level"), defaultLevel)
            );
        }
        return normalizedLogger;
    }

    private static String getLogPath(Map<String, Object> loggingConfig) {
        Object logPath = loggingConfig.getOrDefault(
            "log_path",
            DefaultLogConstants.defaultInnerLogConfig().getOrDefault("log_path", "./logs/")
        );
        return LoggingUtils.normalizeAndValidateLogPath(logPath);
    }

    private static String resolveLogFile(String logPath, String logFile) {
        String expandedLogFile = expandUser(logFile);
        Path logFilePath = Path.of(expandedLogFile);
        String fullLogFile = logFilePath.isAbsolute()
            ? logFilePath.normalize().toString()
            : Path.of(logPath, logFile).normalize().toString();
        return LoggingUtils.normalizeAndValidateLogPath(fullLogFile);
    }

    private static Integer getLoggerLevelOverride(Map<String, Object> loggingConfig, String logType) {
        Object loggersConfig = loggingConfig.get("loggers");
        if (!(loggersConfig instanceof Map<?, ?> rawLoggerMap)) {
            return null;
        }

        Object loggerConfig = rawLoggerMap.get(logType);
        if (!(loggerConfig instanceof Map<?, ?> rawConfig) || !rawConfig.containsKey("level")) {
            return null;
        }
        return LogLevels.normalizeLogLevel(rawConfig.get("level"), LogLevels.WARNING);
    }

    private static Map<String, Object> mapFrom(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), deepCopy(value)));
        return result;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(key, deepCopy(value)));
        return result;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            return mapFrom(rawMap);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(deepCopy(item)));
            return result;
        }
        return value;
    }

    private static String expandUser(String path) {
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private record LoggerBaseKeys(String logFileKey, String defaultLogFile, String outputKey, Object defaultOutput) {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.loguru.LoguruConfigProvider;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Log configuration 鈥?resolves per-logger configs from YAML or in-memory maps.
 *
 * <p>Mirrors Python's default backend config helpers in
 * {@code openjiuwen/core/common/logging/log_config.py}.</p>
 */
public class LogConfig {

    private static final String[] BUILTIN_LOG_TYPES = {"common", "interface", "prompt_builder", "performance"};

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
        "backend", "level", "structured_output_format", "backup_count", "max_bytes", "format", "log_path",
        "log_file", "output", "interface_log_file", "interface_output", "prompt_builder_interface_log_file",
        "performance_log_file", "performance_output", "log_file_pattern", "backup_file_pattern", "propagate",
        "loggers"
    );

    private static final Set<String> DEFAULT_ALLOWED_LOGGER_KEYS = Set.of("level");

    private Map<String, Object> logConfig;

    public LogConfig() {
        this(null);
    }

    public LogConfig(String configPath) {
        if (configPath == null) {
            this.logConfig = normalizeLoadedConfig(DefaultLogConstants.defaultInnerLogConfig());
        } else {
            this.logConfig = loadConfig(configPath);
        }
    }

    public void reload(String configPath) {
        this.logConfig = loadConfig(configPath);
    }

    public void loadFromDict(Map<String, Object> loggingConfig) {
        this.logConfig = normalizeLoadedConfig(deepCopyMap(loggingConfig));
    }

    public Map<String, Object> getSnapshot() {
        return deepCopyMap(logConfig);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadConfig(String configPath) {
        try {
            String realPath = LoggingUtils.normalizeAndValidateLogPath(configPath);
            try (InputStream is = Files.newInputStream(Path.of(realPath))) {
                Yaml yaml = new Yaml();
                Map<String, Object> full = yaml.load(is);
                if (full == null || !full.containsKey("logging")) {
                    throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                        "error_msg", "YAML configuration file is missing 'logging' section");
                }
                return normalizeLoadedConfig(asMap(full.get("logging")));
            }
        } catch (java.nio.file.NoSuchFileException | java.io.FileNotFoundException e) {
            return normalizeLoadedConfig(DefaultLogConstants.defaultInnerLogConfig());
        } catch (org.yaml.snakeyaml.error.YAMLException e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR,
                "error_msg", "YAML configuration file format is incorrect: " + e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR,
                "error_msg", "failed to read configuration file: " + e);
        }
    }

    private static Map<String, Object> normalizeLoadedConfig(Map<String, Object> loggingConfig) {
        String backend = LogLevels.extractBackend(loggingConfig);
        return switch (backend) {
            case "default" -> loadDefaultBackendConfig(loggingConfig);
            case "loguru" -> LoguruConfigProvider.loadLoguruBackendConfig(loggingConfig);
            default -> throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg", "unsupported logging backend '" + backend + "'");
        };
    }

    public Map<String, Object> getLoggerConfig(String logType) {
        return getLoggerConfig(logType, null);
    }

    public Map<String, Object> getLoggerConfig(String logType, String backend) {
        String resolvedBackend = backend != null && !backend.isBlank()
            ? backend.trim().toLowerCase()
            : getBackend();
        return switch (resolvedBackend) {
            case "default" -> buildDefaultLoggerConfig(logConfig, logType);
            case "loguru" -> LoguruConfigProvider.buildLoguruLoggerConfig(logConfig, logType);
            default -> throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg", "unsupported logging backend '" + resolvedBackend + "'");
        };
    }

    public Map<String, Object> getCommonConfig() {
        return getCommonConfig(null);
    }

    public Map<String, Object> getCommonConfig(String backend) {
        return getLoggerConfig("common", backend);
    }

    public Map<String, Object> getInterfaceConfig() {
        return getInterfaceConfig(null);
    }

    public Map<String, Object> getInterfaceConfig(String backend) {
        return getLoggerConfig("interface", backend);
    }

    public Map<String, Object> getPromptBuilderConfig() {
        return getPromptBuilderConfig(null);
    }

    public Map<String, Object> getPromptBuilderConfig(String backend) {
        return getLoggerConfig("prompt_builder", backend);
    }

    public Map<String, Object> getPerformanceConfig() {
        return getPerformanceConfig(null);
    }

    public Map<String, Object> getPerformanceConfig(String backend) {
        return getLoggerConfig("performance", backend);
    }

    public Map<String, Object> getCustomConfig(String logType) {
        return getCustomConfig(logType, null);
    }

    public Map<String, Object> getCustomConfig(String logType, String backend) {
        return getLoggerConfig(logType, backend);
    }

    public String getBackend() {
        return LogLevels.extractBackend(logConfig);
    }

    public Map<String, Map<String, Object>> getAllConfigs() {
        return getAllConfigs(null);
    }

    public Map<String, Map<String, Object>> getAllConfigs(String backend) {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        Set<String> logTypes = new LinkedHashSet<>(List.of(BUILTIN_LOG_TYPES));
        Map<String, Object> extraLoggers = asMapOrNull(logConfig.get("loggers"));
        if (extraLoggers != null) {
            logTypes.addAll(extraLoggers.keySet());
        }
        for (String logType : logTypes) {
            all.put(logType, getLoggerConfig(logType, backend));
        }
        return all;
    }

    private static Map<String, Object> normalizeDefaultLoggingConfig(Map<String, Object> loggingConfig) {
        Map<String, Object> normalized = deepCopyMap(loggingConfig);
        normalized.put("backend", "default");
        normalized.put("level", LogLevels.normalizeLogLevel(
            normalized.getOrDefault("level", DefaultLogConstants.DEFAULT_LEVEL), LogLevels.WARNING));

        Object loggersConfig = normalized.get("loggers");
        if (loggersConfig == null) {
            normalized.put("loggers", new LinkedHashMap<String, Object>());
        } else if (loggersConfig instanceof Map<?, ?> loggerMap) {
            Map<String, Object> normalizedLoggers = new LinkedHashMap<>();
            loggerMap.forEach((loggerName, loggerConfig) ->
                normalizedLoggers.put(String.valueOf(loggerName), normalizeDefaultLoggerConfig(loggerConfig)));
            normalized.put("loggers", normalizedLoggers);
        }
        return normalized;
    }

    private static Object normalizeDefaultLoggerConfig(Object loggerConfig) {
        Map<String, Object> normalized = asMapOrNull(loggerConfig);
        if (normalized == null) {
            return loggerConfig;
        }
        if (normalized.containsKey("level")) {
            normalized.put("level", LogLevels.normalizeLogLevel(normalized.get("level"), LogLevels.WARNING));
        }
        return normalized;
    }

    private static void validateDefaultBackendConfig(Map<String, Object> loggingConfig) {
        Set<String> unknownKeys = new LinkedHashSet<>(loggingConfig.keySet());
        unknownKeys.removeAll(DEFAULT_ALLOWED_ROOT_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg", "default backend config has unsupported keys: " + unknownKeys);
        }

        Object loggersConfig = loggingConfig.get("loggers");
        if (loggersConfig == null) {
            return;
        }
        if (!(loggersConfig instanceof Map<?, ?> loggerMap)) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg", "default backend config 'loggers' must be a mapping");
        }
        loggerMap.forEach((loggerName, loggerConfig) -> {
            Map<String, Object> typedLogger = asMapOrNull(loggerConfig);
            if (typedLogger == null) {
                throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                    "error_msg", "default logger config for '" + loggerName + "' must be a mapping");
            }
            Set<String> unknownLoggerKeys = new LinkedHashSet<>(typedLogger.keySet());
            unknownLoggerKeys.removeAll(DEFAULT_ALLOWED_LOGGER_KEYS);
            if (!unknownLoggerKeys.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                    "error_msg", "default logger '" + loggerName + "' has unsupported keys: " + unknownLoggerKeys);
            }
        });
    }

    private static Map<String, Object> loadDefaultBackendConfig(Map<String, Object> loggingConfig) {
        Map<String, Object> normalized = normalizeDefaultLoggingConfig(loggingConfig);
        validateDefaultBackendConfig(normalized);
        return normalized;
    }

    private static Map<String, Object> buildDefaultLoggerConfig(Map<String, Object> loggingConfig, String logType) {
        String logPath = getLogPath(loggingConfig);
        LoggerBaseKeys loggerKeys = LOGGER_BASE_KEYS.get(logType);
        String defaultLogFile = loggerKeys != null ? loggerKeys.defaultLogFile : logType + ".log";
        Object defaultOutput = loggerKeys != null ? loggerKeys.defaultOutput
            : DefaultLogConstants.defaultInnerLogConfig().get("output");
        String logFileKey = loggerKeys != null ? loggerKeys.logFileKey : null;
        String outputKey = loggerKeys != null ? loggerKeys.outputKey : "output";

        Object configuredLogFile = logFileKey != null
            ? loggingConfig.getOrDefault(logFileKey, defaultLogFile)
            : defaultLogFile;
        Object configuredOutput = loggingConfig.getOrDefault(outputKey, defaultOutput);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", "default");
        config.put("log_file", resolveLogFile(logPath, String.valueOf(configuredLogFile)));
        config.put("output", configuredOutput);
        config.put("level", LogLevels.normalizeLogLevel(
            loggingConfig.getOrDefault("level", DefaultLogConstants.DEFAULT_LEVEL), LogLevels.WARNING));
        config.put("structured_output_format", loggingConfig.getOrDefault(
            "structured_output_format", DefaultLogConstants.DEFAULT_STRUCTURED_OUTPUT_FORMAT));
        config.put("backup_count", loggingConfig.getOrDefault("backup_count", DefaultLogConstants.DEFAULT_BACKUP_COUNT));
        config.put("max_bytes", loggingConfig.getOrDefault("max_bytes", DefaultLogConstants.DEFAULT_MAX_BYTES));
        config.put("format", loggingConfig.getOrDefault("format", DefaultLogConstants.DEFAULT_FORMAT));
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

    private static String getLogPath(Map<String, Object> loggingConfig) {
        Object logPath = loggingConfig.getOrDefault("log_path", DefaultLogConstants.DEFAULT_LOG_PATH);
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
        Map<String, Object> loggersConfig = asMapOrNull(loggingConfig.get("loggers"));
        if (loggersConfig == null) {
            return null;
        }
        Map<String, Object> loggerConfig = asMapOrNull(loggersConfig.get(logType));
        if (loggerConfig == null || !loggerConfig.containsKey("level")) {
            return null;
        }
        return LogLevels.normalizeLogLevel(loggerConfig.get("level"), LogLevels.WARNING);
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> result = asMapOrNull(value);
        if (result == null) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID,
                "error_msg", "logging config must be a mapping");
        }
        return result;
    }

    private static Map<String, Object> asMapOrNull(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), deepCopy(mapValue)));
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
            Map<String, Object> copied = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> copied.put(String.valueOf(key), deepCopy(mapValue)));
            return copied;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof String)) {
            java.util.List<Object> copied = new java.util.ArrayList<>();
            iterable.forEach(item -> copied.add(deepCopy(item)));
            return copied;
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


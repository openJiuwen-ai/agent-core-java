/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LoggingUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loguru backend configuration normalization and validation helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.common.logging.loguru.config_provider}.</p>
 */
public final class LoguruConfigProvider {

    private static final Set<String> LOGURU_ALLOWED_ROOT_KEYS = Set.of(
        "backend", "level", "defaults", "sinks", "routes", "loggers"
    );
    private static final Set<String> LOGURU_ALLOWED_SINK_KEYS = Set.of(
        "target", "level", "serialize", "serialize_mode", "format", "colorize", "enqueue", "catch",
        "backtrace", "diagnose", "rotation", "retention", "compression", "encoding"
    );
    private static final Set<String> LOGURU_ALLOWED_LOGGER_KEYS = Set.of("level");
    private static final Set<String> STD_STREAM_TARGETS = Set.of("stdout", "stderr");
    private static final Set<String> LOGURU_SERIALIZE_MODES = Set.of("loguru", "event");

    private LoguruConfigProvider() {
    }

    /**
     * Normalize the backend config, preserving Python's merge and level-dispatch behavior.
     */
    public static Map<String, Object> normalizeLoguruLoggingConfig(Object loggingConfig) {
        return normalizeLoguruLoggingConfig(loggingConfig, LogLevels.INFO);
    }

    /**
     * Normalize the backend config, preserving Python's merge and level-dispatch behavior.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeLoguruLoggingConfig(Object loggingConfig, int defaultLevel) {
        if (!(loggingConfig instanceof Map<?, ?> rawMap)) {
            return deepCopyMap(LoguruConstants.defaultInnerLogConfig());
        }

        Map<String, Object> normalizedConfig = mapFrom(rawMap);
        normalizedConfig.put("backend", "loguru");

        Map<String, Object> defaultsConfig = asMap(normalizedConfig.get("defaults"));
        if (defaultsConfig == null) {
            defaultsConfig = new LinkedHashMap<>();
        }

        Map<String, Object> defaultInner = LoguruConstants.defaultInnerLogConfig();
        Map<String, Object> defaultDefaults = asMap(defaultInner.get("defaults"));
        int effectiveDefaultLevel = LogLevels.normalizeLogLevel(
            defaultsConfig.getOrDefault("level",
                normalizedConfig.getOrDefault("level", defaultDefaults.getOrDefault("level", defaultLevel))),
            defaultLevel
        );

        Map<String, Object> mergedDefaults = deepCopyMap(defaultDefaults);
        mergedDefaults.putAll(defaultsConfig);
        mergedDefaults.put("level", effectiveDefaultLevel);
        normalizedConfig.put("level", effectiveDefaultLevel);
        normalizedConfig.put("defaults", mergedDefaults);

        Map<String, Object> sinksConfig = asMap(normalizedConfig.get("sinks"));
        if (sinksConfig != null) {
            Map<String, Object> sinks = new LinkedHashMap<>();
            sinksConfig.forEach((sinkName, sinkConfig) ->
                sinks.put(sinkName, normalizeLoguruSinkConfig(sinkConfig, effectiveDefaultLevel)));
            normalizedConfig.put("sinks", sinks);
        } else {
            normalizedConfig.put("sinks", new LinkedHashMap<String, Object>());
        }

        Map<String, Object> routesConfig = asMap(normalizedConfig.get("routes"));
        if (routesConfig != null) {
            Map<String, Object> routes = new LinkedHashMap<>();
            routesConfig.forEach((routeName, routeTargets) ->
                routes.put(routeName, normalizeRouteTargets(routeName, routeTargets)));
            normalizedConfig.put("routes", routes);
        } else {
            normalizedConfig.put("routes", new LinkedHashMap<String, Object>());
        }

        Object loggersConfig = normalizedConfig.get("loggers");
        if (loggersConfig == null) {
            normalizedConfig.put("loggers", new LinkedHashMap<String, Object>());
        } else if (loggersConfig instanceof Map<?, ?> loggerMap) {
            Map<String, Object> loggers = new LinkedHashMap<>();
            loggerMap.forEach((loggerName, loggerConfig) ->
                loggers.put(String.valueOf(loggerName), normalizeLoguruLoggerConfig(loggerConfig,
                    effectiveDefaultLevel)));
            normalizedConfig.put("loggers", loggers);
        }

        return normalizedConfig;
    }

    /**
     * Validate and load a Loguru backend config.
     */
    public static Map<String, Object> loadLoguruBackendConfig(Map<String, Object> loggingConfig) {
        Map<String, Object> normalizedConfig = normalizeLoguruLoggingConfig(loggingConfig);
        validateLoguruBackendConfig(normalizedConfig);
        return normalizedConfig;
    }

    /**
     * Validate root, sink, route and per-logger config shape.
     */
    public static void validateLoguruBackendConfig(Map<String, Object> loggingConfig) {
        Set<String> unknownKeys = new LinkedHashSet<>(loggingConfig.keySet());
        unknownKeys.removeAll(LOGURU_ALLOWED_ROOT_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw configError("loguru backend config has unsupported keys: " + new ArrayList<>(unknownKeys));
        }

        Map<String, Object> sinksConfig = asMap(loggingConfig.get("sinks"));
        if (sinksConfig == null || sinksConfig.isEmpty()) {
            throw configError("loguru config requires a non-empty 'sinks' mapping");
        }
        sinksConfig.forEach(LoguruConfigProvider::validateLoguruSinkTemplate);

        Map<String, Object> routesConfig = asMap(loggingConfig.get("routes"));
        if (routesConfig == null || routesConfig.isEmpty()) {
            throw configError("loguru config requires a non-empty 'routes' mapping");
        }
        routesConfig.forEach((routeName, routeTargets) ->
            validateSinkNameList(routeName, routeTargets, sinksConfig));

        Object loggersConfig = loggingConfig.get("loggers");
        if (loggersConfig == null) {
            return;
        }
        if (!(loggersConfig instanceof Map<?, ?> loggerMap)) {
            throw configError("loguru config 'loggers' must be a mapping");
        }
        loggerMap.forEach((loggerName, loggerConfig) ->
            validateLoguruLoggerTemplate(String.valueOf(loggerName), loggerConfig));
    }

    /**
     * Build the materialized per-logger configuration.
     */
    public static Map<String, Object> buildLoguruLoggerConfig(Map<String, Object> loggingConfig, String logType) {
        Map<String, Object> defaults = deepCopyMap(asMapOrEmpty(
            loggingConfig.getOrDefault("defaults", LoguruConstants.defaultInnerLogConfig().get("defaults"))));
        Map<String, Object> baseSinks = deepCopyMap(asMapOrEmpty(loggingConfig.get("sinks")));
        Integer effectiveLevel = getLoggerLevelOverride(loggingConfig, logType);
        if (effectiveLevel == null) {
            effectiveLevel = LogLevels.normalizeLogLevel(defaults.getOrDefault("level", LogLevels.INFO),
                LogLevels.INFO);
        }
        List<String> effectiveSinkNames = resolveRouteSinkNames(loggingConfig, logType);

        Map<String, Object> sinkDefaults = new LinkedHashMap<>();
        defaults.forEach((key, value) -> {
            if (LOGURU_ALLOWED_SINK_KEYS.contains(key)) {
                sinkDefaults.put(key, value);
            }
        });

        List<Map<String, Object>> materializedSinks = new ArrayList<>();
        for (String sinkName : effectiveSinkNames) {
            if (!baseSinks.containsKey(sinkName)) {
                throw configError("loguru logger '" + logType + "' references unknown sink '" + sinkName + "'");
            }

            Map<String, Object> sinkConfig = deepCopyMap(sinkDefaults);
            sinkConfig.putAll(asMapOrEmpty(baseSinks.get(sinkName)));
            sinkConfig.put("name", sinkName);
            sinkConfig.put("target", resolveLoguruTarget(sinkConfig.get("target")));
            sinkConfig.put("level", LogLevels.normalizeLogLevel(
                sinkConfig.getOrDefault("level", defaults.getOrDefault("level", LogLevels.INFO)),
                LogLevels.INFO));
            materializedSinks.add(sinkConfig);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", "loguru");
        config.put("level", effectiveLevel);
        config.put("effective_level", effectiveLevel);
        config.put("sinks", materializedSinks);
        return config;
    }

    /**
     * Resolve route sink names for a logger, falling back to the "*" route.
     */
    public static List<String> resolveRouteSinkNames(Map<String, Object> loggingConfig, String logType) {
        Map<String, Object> routesConfig = asMap(loggingConfig.get("routes"));
        if (routesConfig == null) {
            routesConfig = Map.of();
        }
        Object routeTargets = routesConfig.get(logType);
        if (routeTargets == null) {
            routeTargets = routesConfig.get("*");
        }
        if (routeTargets == null) {
            throw configError("loguru logger '" + logType
                + "' does not have a route and no '*' fallback is configured");
        }
        return toStringList(routeTargets);
    }

    /**
     * Resolve std stream targets or an absolute validated filesystem target.
     */
    public static String resolveLoguruTarget(Object target) {
        if (!(target instanceof String targetText) || targetText.isBlank()) {
            throw configError("loguru sink target is invalid: " + target);
        }
        String normalizedTarget = targetText.trim();
        String loweredTarget = normalizedTarget.toLowerCase(Locale.ROOT);
        if (STD_STREAM_TARGETS.contains(loweredTarget)) {
            return loweredTarget;
        }
        String expanded = expandUser(normalizedTarget);
        String absolute = Path.of(expanded).toAbsolutePath().normalize().toString();
        return LoggingUtils.normalizeAndValidateLogPath(absolute);
    }

    private static Object normalizeLoguruSinkConfig(Object sinkConfig, int defaultLevel) {
        Map<String, Object> normalizedSink = asMap(sinkConfig);
        if (normalizedSink == null) {
            return sinkConfig;
        }
        normalizedSink = deepCopyMap(normalizedSink);
        if (normalizedSink.containsKey("level")) {
            normalizedSink.put("level", LogLevels.normalizeLogLevel(normalizedSink.get("level"), defaultLevel));
        }
        Object serializeMode = normalizedSink.get("serialize_mode");
        if (serializeMode instanceof String mode) {
            normalizedSink.put("serialize_mode", mode.trim().toLowerCase(Locale.ROOT));
        }
        return normalizedSink;
    }

    private static Object normalizeRouteTargets(String routeName, Object routeTargets) {
        if (!(routeTargets instanceof Iterable<?> iterable)) {
            return routeTargets;
        }
        List<Object> normalizedTargets = new ArrayList<>();
        for (Object sinkName : iterable) {
            if (sinkName instanceof String text && !text.isBlank()) {
                normalizedTargets.add(text.trim());
            } else {
                normalizedTargets.add(sinkName);
            }
        }
        return normalizedTargets;
    }

    private static Object normalizeLoguruLoggerConfig(Object loggerConfig, int defaultLevel) {
        Map<String, Object> normalizedLogger = asMap(loggerConfig);
        if (normalizedLogger == null) {
            return loggerConfig;
        }
        normalizedLogger = deepCopyMap(normalizedLogger);
        if (normalizedLogger.containsKey("level")) {
            normalizedLogger.put("level", LogLevels.normalizeLogLevel(normalizedLogger.get("level"), defaultLevel));
        }
        return normalizedLogger;
    }

    private static void validateLoguruSinkTemplate(String sinkName, Object sinkConfig) {
        Map<String, Object> typedSink = asMap(sinkConfig);
        if (typedSink == null) {
            throw configError("loguru sink config must be a mapping, got "
                + (sinkConfig == null ? "null" : sinkConfig.getClass()));
        }
        Set<String> unknownKeys = new LinkedHashSet<>(typedSink.keySet());
        unknownKeys.removeAll(LOGURU_ALLOWED_SINK_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw configError("loguru sink '" + sinkName + "' has unsupported keys: " + new ArrayList<>(unknownKeys));
        }
        if (!typedSink.containsKey("target")) {
            throw configError("loguru sink '" + sinkName + "' is missing required key 'target'");
        }
        resolveLoguruTarget(typedSink.get("target"));

        Object serializeMode = typedSink.get("serialize_mode");
        if (serializeMode != null && !LOGURU_SERIALIZE_MODES.contains(String.valueOf(serializeMode))) {
            throw configError("loguru sink '" + sinkName + "' has invalid serialize_mode '" + serializeMode
                + "', expected one of " + new ArrayList<>(LOGURU_SERIALIZE_MODES));
        }
    }

    private static void validateLoguruLoggerTemplate(String loggerName, Object loggerConfig) {
        Map<String, Object> typedLogger = asMap(loggerConfig);
        if (typedLogger == null) {
            throw configError("loguru logger '" + loggerName + "' must be a mapping");
        }
        Set<String> unknownKeys = new LinkedHashSet<>(typedLogger.keySet());
        unknownKeys.removeAll(LOGURU_ALLOWED_LOGGER_KEYS);
        if (!unknownKeys.isEmpty()) {
            throw configError("loguru logger '" + loggerName + "' has unsupported keys: "
                + new ArrayList<>(unknownKeys));
        }
    }

    private static void validateSinkNameList(String routeName, Object sinkNames, Map<String, Object> sinksConfig) {
        if (!(sinkNames instanceof List<?> list)) {
            throw configError("loguru route '" + routeName + "' must be a list of sink names");
        }
        for (Object sinkName : list) {
            if (!(sinkName instanceof String name) || !sinksConfig.containsKey(name)) {
                throw configError("loguru route '" + routeName + "' references unknown sink '" + sinkName + "'");
            }
        }
    }

    private static Integer getLoggerLevelOverride(Map<String, Object> loggingConfig, String logType) {
        Map<String, Object> loggersConfig = asMap(loggingConfig.get("loggers"));
        if (loggersConfig == null) {
            return null;
        }
        Map<String, Object> loggerConfig = asMap(loggersConfig.get(logType));
        if (loggerConfig == null || !loggerConfig.containsKey("level")) {
            return null;
        }
        return LogLevels.normalizeLogLevel(loggerConfig.get("level"), LogLevels.INFO);
    }

    private static RuntimeException configError(String message) {
        return ErrorHelper.buildError(StatusCode.COMMON_LOG_CONFIG_INVALID, "error_msg", message);
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            throw configError("loguru route target must be a list of sink names");
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static Map<String, Object> mapFrom(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), deepCopy(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        return mapFrom(rawMap);
    }

    private static Map<String, Object> asMapOrEmpty(Object value) {
        Map<String, Object> map = asMap(value);
        return map != null ? map : new LinkedHashMap<>();
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
}

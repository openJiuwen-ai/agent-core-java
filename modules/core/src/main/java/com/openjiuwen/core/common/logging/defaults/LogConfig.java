/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.logging.LoggingUtils;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Log configuration — resolves per-logger configs from a YAML file.
 * <p>
 * Java equivalent of Python's {@code LogConfig}.
 * 
 * @since 0.1.7
 */
public class LogConfig {
    private static final Map<String, Integer> NAME_TO_LEVEL = Map.of("CRITICAL", 50, "FATAL", 50, "ERROR", 40,
            "WARNING", 30, "WARN", 30, "INFO", 20, "DEBUG", 10, "NOTSET", 0);

    private Map<String, Object> logConfig;
    private String logPath;

    /**
     * LogConfig.
     * 
     * @since 0.1.7
     */
    public LogConfig() {
        this(null);
    }

    /**
     * LogConfig.
     * 
     * @param configPath configPath
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public LogConfig(String configPath) {
        if (configPath == null) {
            this.logConfig = new LinkedHashMap<>(DefaultLogConstants.defaultInnerLogConfig());
        } else {
            this.logConfig = loadConfig(configPath);
        }
        this.logPath = resolveLogPath();
    }

    /**
     * reload.
     * 
     * @param configPath configPath
     * @since 0.1.7
     */
    public void reload(String configPath) {
        this.logConfig = loadConfig(configPath);
        this.logPath = resolveLogPath();
    }

    @SuppressWarnings("unchecked")
    /**
     * loadConfig.
     * 
     * @param configPath configPath
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> loadConfig(String configPath) {
        try (InputStream is = Files.newInputStream(Path.of(configPath))) {
            Yaml yaml = new Yaml();
            Map<String, Object> full = yaml.load(is);
            if (!full.containsKey("logging")) {
                throw new IllegalArgumentException("YAML configuration file is missing 'logging' section");
            }
            return (Map<String, Object>) full.get("logging");
        } catch (java.io.FileNotFoundException e) {
            // Return safe defaults when file is not found
            return safeDefaults();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load log config: " + e.getMessage(), e);
        }
    }

    /**
     * safeDefaults.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> safeDefaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", "WARNING");
        m.put("output", "console");
        m.put("log_path", "./logs/");
        m.put("log_file", DefaultLogConstants.DEFAULT_LOG_FILE);
        m.put("interface_log_file", DefaultLogConstants.DEFAULT_INTERFACE_LOG_FILE);
        m.put("prompt_builder_interface_log_file", DefaultLogConstants.DEFAULT_PROMPT_BUILDER_LOG_FILE);
        m.put("performance_log_file", DefaultLogConstants.DEFAULT_PERFORMANCE_LOG_FILE);
        m.put("backup_count", DefaultLogConstants.DEFAULT_BACKUP_COUNT);
        m.put("max_bytes", DefaultLogConstants.DEFAULT_MAX_BYTES);
        m.put("format", DefaultLogConstants.DEFAULT_FORMAT);
        return m;
    }

    /**
     * resolveLogPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String resolveLogPath() {
        return String.valueOf(logConfig.getOrDefault("log_path", "./logs/"));
    }

    /**
     * Build a base per-logger config for a given log file.
     * 
     * @param logFile logFile
     * @param output output
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> getBaseConfig(String logFile, String output) {
        String levelStr = String.valueOf(logConfig.getOrDefault("level", "INFO")).toUpperCase(Locale.ROOT);
        int levelValue = NAME_TO_LEVEL.getOrDefault(levelStr, 20);

        if (output == null) {
            output = String.valueOf(logConfig.getOrDefault("output", "console,file"));
        }

        String fullLogFile =
            logPath.endsWith("/") || logPath.endsWith("\\") ? logPath + logFile : logPath + "/" + logFile;

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("log_file", fullLogFile);
        cfg.put("output", output);
        cfg.put("level", levelValue);
        cfg.put("backup_count", logConfig.getOrDefault("backup_count", DefaultLogConstants.DEFAULT_BACKUP_COUNT));
        cfg.put("max_bytes", LoggingUtils
                .getLogMaxBytes(logConfig.getOrDefault("max_bytes", DefaultLogConstants.DEFAULT_MAX_BYTES)));
        cfg.put("format", logConfig.getOrDefault("format", DefaultLogConstants.DEFAULT_FORMAT));
        return cfg;
    }

    /**
     * getCommonConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getCommonConfig() {
        return getBaseConfig(String.valueOf(logConfig.getOrDefault("log_file", DefaultLogConstants.DEFAULT_LOG_FILE)),
                null);
    }

    /**
     * getInterfaceConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getInterfaceConfig() {
        return getBaseConfig(
                String.valueOf(
                        logConfig.getOrDefault("interface_log_file", DefaultLogConstants.DEFAULT_INTERFACE_LOG_FILE)),
                String.valueOf(logConfig.getOrDefault("interface_output", "console,file")));
    }

    /**
     * getPromptBuilderConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getPromptBuilderConfig() {
        return getBaseConfig(
                String.valueOf(logConfig.getOrDefault("prompt_builder_interface_log_file",
                        DefaultLogConstants.DEFAULT_PROMPT_BUILDER_LOG_FILE)),
                String.valueOf(logConfig.getOrDefault("interface_output", "console,file")));
    }

    /**
     * getPerformanceConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getPerformanceConfig() {
        return getBaseConfig(
                String.valueOf(logConfig.getOrDefault("performance_log_file",
                        DefaultLogConstants.DEFAULT_PERFORMANCE_LOG_FILE)),
                String.valueOf(logConfig.getOrDefault("performance_output", "console,file")));
    }

    /**
     * getCustomConfig.
     * 
     * @param logType logType
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getCustomConfig(String logType) {
        return getBaseConfig(logType + ".log", null);
    }

    /**
     * Get all standard logger configurations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Map<String, Object>> getAllConfigs() {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        all.put("common", getCommonConfig());
        all.put("interface", getInterfaceConfig());
        all.put("prompt_builder", getPromptBuilderConfig());
        all.put("performance", getPerformanceConfig());
        return all;
    }
}

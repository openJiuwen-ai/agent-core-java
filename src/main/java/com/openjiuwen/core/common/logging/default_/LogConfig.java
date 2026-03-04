// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging.default_;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openjiuwen.core.common.security.PathChecker;

/**
 * 日志配置单例
 *
 * <p>对应Python版本: openjiuwen/core/common/logging/default/log_config.py</p>
 *
 * <p>提供全局日志配置管理。</p>
 */
public final class LogConfig {

    private static volatile LogConfig instance;
    private static final Object LOCK = new Object();

    private final LogConfigManager configManager;
    private String logPath;
    private Map<String, Object> logConfig;

    /**
     * 私有构造函数
     *
     * @param configPath 配置文件路径（可选）
     */
    private LogConfig(String configPath) {
        this.configManager = new LogConfigManager(configPath);
        this.logConfig = configManager.getConfig();
        this.logPath = (String) logConfig.getOrDefault("log_path", "./logs/");
    }

    /**
     * 获取LogConfig单例实例
     *
     * @return LogConfig实例
     */
    public static LogConfig getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new LogConfig(null);
                }
            }
        }
        return instance;
    }

    /**
     * 重新加载配置
     *
     * <p>根据Python版本行为，重新加载配置文件并更新内部状态。</p>
     *
     * @param configPath 配置文件路径
     */
    public void reload(String configPath) {
        configManager.reload(configPath);
        // 重新获取配置并更新内部状态
        this.logConfig = configManager.getConfig();
        this.logPath = (String) logConfig.getOrDefault("log_path", "./logs/");
    }

    /**
     * 获取日志路径
     *
     * @return 日志路径
     */
    public String getLogPath() {
        return logPath;
    }

    /**
     * 获取基础配置
     *
     * @param logFile 日志文件名
     * @param output 输出目标列表
     * @return 基础配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBaseConfig(String logFile, List<String> output) {
        String levelStr = ((String) logConfig.getOrDefault("level", "INFO")).toUpperCase();
        int level = LogConstants.parseLevel(levelStr);

        if (output == null) {
            output = (List<String>) logConfig.getOrDefault("output", List.of("console", "file"));
        }

        String fullLogFile = Paths.get(logPath, logFile).toString();
        normalizeAndValidateLogPath(fullLogFile);

        Map<String, Object> config = new HashMap<>();
        config.put("log_file", fullLogFile);
        config.put("output", output);
        config.put("level", level);
        config.put("backup_count", logConfig.getOrDefault("backup_count", 20));
        config.put("max_bytes", logConfig.getOrDefault("max_bytes", 20971520L));
        config.put("format", logConfig.getOrDefault(
            "format", "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s"
        ));
        config.put("log_file_pattern", logConfig.get("log_file_pattern"));
        config.put("backup_file_pattern", logConfig.get("backup_file_pattern"));

        return config;
    }

    /**
     * 获取通用日志配置
     *
     * @return 通用日志配置
     */
    public Map<String, Object> getCommonConfig() {
        return getBaseConfig(
            (String) logConfig.getOrDefault("log_file", "run/jiuwen.log"),
            null
        );
    }

    /**
     * 获取接口日志配置
     *
     * @return 接口日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInterfaceConfig() {
        return getBaseConfig(
            (String) logConfig.getOrDefault("interface_log_file", "interface/jiuwen_interface.log"),
            (List<String>) logConfig.getOrDefault("interface_output", List.of("console", "file"))
        );
    }

    /**
     * 获取Prompt Builder接口日志配置
     *
     * @return Prompt Builder接口日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPromptBuilderConfig() {
        return getBaseConfig(
            (String) logConfig.getOrDefault("prompt_builder_interface_log_file", "interface/jiuwen_prompt_builder_interface.log"),
            (List<String>) logConfig.getOrDefault("interface_output", List.of("console", "file"))
        );
    }

    /**
     * 获取性能日志配置
     *
     * @return 性能日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPerformanceConfig() {
        return getBaseConfig(
            (String) logConfig.getOrDefault("performance_log_file", "performance/jiuwen_performance.log"),
            (List<String>) logConfig.getOrDefault("performance_output", List.of("console", "file"))
        );
    }

    /**
     * 获取自定义日志配置
     *
     * @param logType 日志类型
     * @return 自定义日志配置
     */
    public Map<String, Object> getCustomConfig(String logType) {
        return getCustomConfig(logType, null);
    }

    /**
     * 获取自定义日志配置
     *
     * @param logType 日志类型
     * @param kwargs 额外配置参数
     * @return 自定义日志配置
     */
    public Map<String, Object> getCustomConfig(String logType, Map<String, Object> kwargs) {
        Map<String, Object> baseConfig = getBaseConfig(logType + ".log", null);
        if (kwargs != null) {
            baseConfig.putAll(kwargs);
        }
        return baseConfig;
    }

    /**
     * 获取所有配置
     *
     * @return 所有配置映射
     */
    public Map<String, Map<String, Object>> getAllConfigs() {
        Map<String, Map<String, Object>> allConfigs = new HashMap<>();
        allConfigs.put("common", getCommonConfig());
        allConfigs.put("interface", getInterfaceConfig());
        allConfigs.put("prompt_builder", getPromptBuilderConfig());
        allConfigs.put("performance", getPerformanceConfig());
        return allConfigs;
    }

    /**
     * 重置单例
     */
    public static void reset() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    /**
     * 规范化并验证日志路径
     *
     * @param path 日志路径
     * @throws IllegalArgumentException 如果路径是敏感路径
     */
    private static void normalizeAndValidateLogPath(String path) {
        try {
            Path normalizedPath = Paths.get(path).toAbsolutePath();
            if (PathChecker.getInstance().isSensitivePath(normalizedPath.toString())) {
                throw new IllegalArgumentException("Sensitive log path: " + normalizedPath);
            }
        } catch (Exception e) {
            // 路径规范化失败时继续，允许默认路径
        }
    }
}
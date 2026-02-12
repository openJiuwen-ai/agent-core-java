/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggingUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志配置类
 * 
 * <p>负责加载和管理日志配置，支持从 YAML 文件加载配置。
 * 
 * <p>对应 Python: default/log_config.py
 */
public class LogConfig {
    
    private Map<String, Object> logConfig;
    private String logPath;
    
    /**
     * 使用默认配置创建 LogConfig
     */
    public LogConfig() {
        this(null);
    }
    
    /**
     * 从配置文件创建 LogConfig
     * 
     * @param configPath 配置文件路径，如果为 null 则使用默认配置
     */
    public LogConfig(String configPath) {
        if (configPath == null) {
            this.logConfig = new HashMap<>(LoggingConstants.DEFAULT_INNER_LOG_CONFIG);
        } else {
            this.logConfig = loadConfig(configPath);
        }
        this.logPath = getLogPathFromConfig();
    }
    
    /**
     * 重新加载配置
     * 
     * @param configPath 配置文件路径
     */
    public void reload(String configPath) {
        this.logConfig = loadConfig(configPath);
        this.logPath = getLogPathFromConfig();
    }
    
    /**
     * 加载配置文件
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig(String configPath) {
        try (InputStream inputStream = new FileInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config == null || !config.containsKey("logging")) {
                throw new JiuWenBaseException(
                    StatusCode.COMMON_LOG_CONFIG_INVALID.getCode(),
                    StatusCode.COMMON_LOG_CONFIG_INVALID.getMessage()
                        .replace("{error_msg}", "YAML configuration file is missing 'logging' section")
                );
            }
            
            return (Map<String, Object>) config.get("logging");
            
        } catch (IOException e) {
            // 文件未找到时提供安全的默认配置
            return getDefaultFallbackConfig();
        } catch (Exception e) {
            if (e instanceof JiuWenBaseException) {
                throw (JiuWenBaseException) e;
            }
            throw new JiuWenBaseException(
                StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getCode(),
                StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getMessage()
                    .replace("{error_msg}", "unexpected error while loading configuration file: " + e.getMessage())
            );
        }
    }
    
    /**
     * 获取默认回退配置
     */
    private Map<String, Object> getDefaultFallbackConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("level", "WARNING");
        config.put("output", List.of("console"));
        config.put("log_path", "./logs/");
        config.put("log_file", "run/jiuwen.log");
        config.put("interface_log_file", "interface/jiuwen_interface.log");
        config.put("prompt_builder_interface_log_file", "interface/jiuwen_prompt_builder_interface.log");
        config.put("performance_log_file", "performance/jiuwen_performance.log");
        config.put("backup_count", 20);
        config.put("max_bytes", 20971520);
        config.put("format", "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s");
        config.put("log_file_pattern", null);
        config.put("backup_file_pattern", null);
        return config;
    }
    
    /**
     * 从配置中获取日志路径
     */
    private String getLogPathFromConfig() {
        String path = (String) logConfig.getOrDefault("log_path", "./logs/");
        LoggingUtils.normalizeAndValidateLogPath(path);
        return path;
    }
    
    /**
     * 获取基础配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getBaseConfig(String logFile, List<String> output) {
        Object levelObj = logConfig.getOrDefault("level", "INFO");
        int levelValue;
        if (levelObj instanceof Integer) {
            levelValue = (Integer) levelObj;
        } else {
            String levelStr = String.valueOf(levelObj).toUpperCase();
            levelValue = LoggingConstants.getLevelByName(levelStr, LoggingConstants.INFO);
        }
        
        if (output == null) {
            Object outputObj = logConfig.get("output");
            if (outputObj instanceof List) {
                output = (List<String>) outputObj;
            } else {
                output = List.of("console", "file");
            }
        }
        
        Path fullLogFile = Paths.get(logPath, logFile);
        LoggingUtils.normalizeAndValidateLogPath(fullLogFile.toString());
        
        Map<String, Object> config = new HashMap<>();
        config.put("log_file", fullLogFile.toString());
        config.put("output", output);
        config.put("level", levelValue);
        config.put("backup_count", logConfig.getOrDefault("backup_count", 20));
        config.put("max_bytes", logConfig.getOrDefault("max_bytes", 20971520));
        config.put("format", logConfig.getOrDefault("format", 
            "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s"));
        config.put("log_file_pattern", logConfig.get("log_file_pattern"));
        config.put("backup_file_pattern", logConfig.get("backup_file_pattern"));
        
        return config;
    }
    
    /**
     * 获取通用日志配置
     */
    public Map<String, Object> getCommonConfig() {
        return getBaseConfig((String) logConfig.getOrDefault("log_file", "run/jiuwen.log"), null);
    }
    
    /**
     * 获取接口日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInterfaceConfig() {
        List<String> output = (List<String>) logConfig.get("interface_output");
        if (output == null) {
            output = List.of("console", "file");
        }
        return getBaseConfig(
            (String) logConfig.getOrDefault("interface_log_file", "interface/jiuwen_interface.log"),
            output
        );
    }
    
    /**
     * 获取 Prompt Builder 日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPromptBuilderConfig() {
        List<String> output = (List<String>) logConfig.get("interface_output");
        if (output == null) {
            output = List.of("console", "file");
        }
        return getBaseConfig(
            (String) logConfig.getOrDefault("prompt_builder_interface_log_file", 
                "interface/jiuwen_prompt_builder_interface.log"),
            output
        );
    }
    
    /**
     * 获取性能日志配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPerformanceConfig() {
        List<String> output = (List<String>) logConfig.get("performance_output");
        if (output == null) {
            output = List.of("console", "file");
        }
        return getBaseConfig(
            (String) logConfig.getOrDefault("performance_log_file", "performance/jiuwen_performance.log"),
            output
        );
    }
    
    /**
     * 获取自定义日志配置
     * 
     * @param logType 日志类型
     * @param overrides 覆盖配置
     * @return 配置字典
     */
    public Map<String, Object> getCustomConfig(String logType, Map<String, Object> overrides) {
        Map<String, Object> baseConfig = getBaseConfig(logType + ".log", null);
        if (overrides != null) {
            baseConfig.putAll(overrides);
        }
        return baseConfig;
    }
    
    /**
     * 获取所有配置
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
     * 获取日志路径
     */
    public String getLogPath() {
        return logPath;
    }
    
    /**
     * 获取原始配置
     */
    public Map<String, Object> getRawConfig() {
        return new HashMap<>(logConfig);
    }
    
    // ==================== 静态实例和配置方法 ====================
    
    private static LogConfig instance = new LogConfig();
    
    /**
     * 获取默认的 LogConfig 实例
     */
    public static LogConfig getInstance() {
        return instance;
    }
    
    /**
     * 配置日志
     * 
     * <p>立即生效到全局 log_config。
     * 
     * @param configPath 配置文件路径
     */
    public static void configureLog(String configPath) {
        instance.reload(configPath);
    }
}


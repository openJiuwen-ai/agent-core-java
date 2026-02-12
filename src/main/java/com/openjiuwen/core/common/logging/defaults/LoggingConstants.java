/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import java.util.List;
import java.util.Map;

/**
 * 日志常量定义
 * 
 * <p>包含日志配置的默认值和常量。
 * 
 * <p>对应 Python: default/constant.py
 */
public final class LoggingConstants {
    
    /**
     * 日志级别常量
     */
    public static final int CRITICAL = 50;
    public static final int FATAL = CRITICAL;
    public static final int ERROR = 40;
    public static final int WARNING = 30;
    public static final int WARN = WARNING;
    public static final int INFO = 20;
    public static final int DEBUG = 10;
    public static final int NOTSET = 0;
    
    /**
     * 日志级别名称到数值的映射
     */
    public static final Map<String, Integer> NAME_TO_LEVEL = Map.ofEntries(
        Map.entry("CRITICAL", CRITICAL),
        Map.entry("FATAL", FATAL),
        Map.entry("ERROR", ERROR),
        Map.entry("WARNING", WARNING),
        Map.entry("WARN", WARN),
        Map.entry("INFO", INFO),
        Map.entry("DEBUG", DEBUG),
        Map.entry("NOTSET", NOTSET)
    );
    
    /**
     * 默认内部日志配置
     */
    public static final Map<String, Object> DEFAULT_INNER_LOG_CONFIG = Map.ofEntries(
        Map.entry("level", "INFO"),
        Map.entry("output", List.of("console", "file")),
        Map.entry("log_path", "./logs/"),
        Map.entry("log_file", "run/jiuwen.log"),
        Map.entry("interface_log_file", "interface/jiuwen_interface.log"),
        Map.entry("interface_output", List.of("console", "file")),
        Map.entry("prompt_builder_interface_log_file", "interface/jiuwen_prompt_builder_interface.log"),
        Map.entry("performance_log_file", "performance/jiuwen_performance.log"),
        Map.entry("performance_output", List.of("console", "file")),
        Map.entry("backup_count", 20),
        Map.entry("max_bytes", 20971520),  // 20 * 1024 * 1024
        Map.entry("format", "%(asctime)s | %(log_type)s | %(filename)s | %(lineno)d | %(funcName)s | %(trace_id)s | %(levelname)s | %(message)s")
    );
    
    /**
     * 默认日志配置（包含 logging 顶级键）
     */
    public static final Map<String, Object> DEFAULT_LOG_CONFIG = Map.of(
        "logging", DEFAULT_INNER_LOG_CONFIG
    );
    
    /**
     * 默认日志格式
     */
    public static final String DEFAULT_LOG_FORMAT = 
        "%d{yyyy-MM-dd HH:mm:ss.SSS} | %X{log_type} | %X{trace_id} | %-5level | %msg%n";
    
    /**
     * 默认日志文件路径
     */
    public static final String DEFAULT_LOG_PATH = "./logs/";
    
    /**
     * 默认最大日志文件大小（字节）
     */
    public static final int DEFAULT_MAX_BYTES = 20 * 1024 * 1024;  // 20MB
    
    /**
     * 默认备份文件数量
     */
    public static final int DEFAULT_BACKUP_COUNT = 20;
    
    /**
     * 默认 trace_id
     */
    public static final String DEFAULT_TRACE_ID = "default_trace_id";
    
    private LoggingConstants() {
        // 工具类，禁止实例化
    }
    
    /**
     * 根据级别名称获取级别数值
     * 
     * @param levelName 级别名称
     * @param defaultLevel 默认级别
     * @return 级别数值
     */
    public static int getLevelByName(String levelName, int defaultLevel) {
        if (levelName == null) {
            return defaultLevel;
        }
        return NAME_TO_LEVEL.getOrDefault(levelName.toUpperCase(), defaultLevel);
    }
}


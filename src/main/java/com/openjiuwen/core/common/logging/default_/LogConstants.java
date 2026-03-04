// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging.default_;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志配置常量
 *
 * <p>对应Python版本: openjiuwen/core/common/logging/default/constant.py</p>
 */
public final class LogConstants {

    /**
     * 私有构造函数，防止实例化
     */
    private LogConstants() {
    }

    /**
     * 默认内部日志配置
     */
    public static final Map<String, Object> DEFAULT_INNER_LOG_CONFIG;
    static {
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        config.put("output", List.of("console", "file"));
        config.put("log_path", "./logs/");
        config.put("log_file", "run/jiuwen.log");
        config.put("interface_log_file", "interface/jiuwen_interface.log");
        config.put("interface_output", List.of("console", "file"));
        config.put("prompt_builder_interface_log_file", "interface/jiuwen_prompt_builder_interface.log");
        config.put("performance_log_file", "performance/jiuwen_performance.log");
        config.put("performance_output", List.of("console", "file"));
        config.put("backup_count", 20);
        config.put("max_bytes", 20971520);
        config.put("format", "%(asctime)s | %(log_type)s | %(filename)s | %(lineno)d | %(funcName)s | %(trace_id)s | %(levelname)s | %(message)s");
        DEFAULT_INNER_LOG_CONFIG = Map.copyOf(config);
    }

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
     * 日志级别名称映射
     */
    public static final Map<String, Integer> NAME_TO_LEVEL;
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put("CRITICAL", CRITICAL);
        map.put("FATAL", FATAL);
        map.put("ERROR", ERROR);
        map.put("WARNING", WARNING);
        map.put("WARN", WARN);
        map.put("INFO", INFO);
        map.put("DEBUG", DEBUG);
        map.put("NOTSET", NOTSET);
        NAME_TO_LEVEL = Map.copyOf(map);
    }

    /**
     * 将级别名称转换为级别值
     *
     * @param levelName 级别名称
     * @return 级别值
     */
    public static int parseLevel(String levelName) {
        return NAME_TO_LEVEL.getOrDefault(levelName.toUpperCase(), WARNING);
    }
}
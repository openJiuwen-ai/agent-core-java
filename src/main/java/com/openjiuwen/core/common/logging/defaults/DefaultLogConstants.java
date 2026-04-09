/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import java.util.Map;

/**
 * Default log configuration constants.
 */
public final class DefaultLogConstants {

    private DefaultLogConstants() {
    }

    public static final String DEFAULT_LEVEL = "INFO";
    public static final String DEFAULT_LOG_PATH = "./logs/";
    public static final String DEFAULT_LOG_FILE = "run/jiuwen.log";
    public static final String DEFAULT_INTERFACE_LOG_FILE = "interface/jiuwen_interface.log";
    public static final String DEFAULT_PROMPT_BUILDER_LOG_FILE = "interface/jiuwen_prompt_builder_interface.log";
    public static final String DEFAULT_PERFORMANCE_LOG_FILE = "performance/jiuwen_performance.log";
    public static final int DEFAULT_BACKUP_COUNT = 20;
    public static final int DEFAULT_MAX_BYTES = 20 * 1024 * 1024; // 20 MB
    public static final String DEFAULT_FORMAT =
        "%d{yyyy-MM-dd HH:mm:ss.SSS} | %X{log_type} | %file | %line | %method | %X{trace_id} | %-5level | %msg%n";

    /**
     * Build the default inner log config as a map.
     */
    public static Map<String, Object> defaultInnerLogConfig() {
        return Map.ofEntries(
            Map.entry("level", DEFAULT_LEVEL),
            Map.entry("output", "console,file"),
            Map.entry("log_path", DEFAULT_LOG_PATH),
            Map.entry("log_file", DEFAULT_LOG_FILE),
            Map.entry("interface_log_file", DEFAULT_INTERFACE_LOG_FILE),
            Map.entry("prompt_builder_interface_log_file", DEFAULT_PROMPT_BUILDER_LOG_FILE),
            Map.entry("performance_log_file", DEFAULT_PERFORMANCE_LOG_FILE),
            Map.entry("performance_output", "console,file"),
            Map.entry("interface_output", "console,file"),
            Map.entry("backup_count", DEFAULT_BACKUP_COUNT),
            Map.entry("max_bytes", DEFAULT_MAX_BYTES),
            Map.entry("format", DEFAULT_FORMAT)
        );
    }

    /**
     * Build the default top-level config map.
     */
    public static Map<String, Object> defaultLogConfig() {
        return Map.of("logging", defaultInnerLogConfig());
    }
}

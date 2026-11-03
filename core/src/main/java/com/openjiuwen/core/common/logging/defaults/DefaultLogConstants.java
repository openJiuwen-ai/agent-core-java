package com.openjiuwen.core.common.logging.defaults;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code DEFAULT_INNER_LOG_CONFIG} in
 * {@code openjiuwen/core/common/logging/default/constant.py}.
 */
public final class DefaultLogConstants {
    public static final String DEFAULT_BACKEND = "default";
    public static final String DEFAULT_LEVEL = "INFO";
    public static final String DEFAULT_STRUCTURED_OUTPUT_FORMAT = "json";
    public static final int DEFAULT_BACKUP_COUNT = 20;
    public static final int DEFAULT_MAX_BYTES = 20_971_520;
    public static final String DEFAULT_FORMAT =
            "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s";
    public static final String DEFAULT_LOG_PATH = "./logs/";
    public static final String DEFAULT_LOG_FILE = "run/jiuwen.log";
    public static final String DEFAULT_INTERFACE_LOG_FILE = "interface/jiuwen_interface.log";
    public static final String DEFAULT_PROMPT_BUILDER_INTERFACE_LOG_FILE =
            "interface/jiuwen_prompt_builder_interface.log";
    public static final String DEFAULT_PERFORMANCE_LOG_FILE = "performance/jiuwen_performance.log";

    private DefaultLogConstants() {
    }

    public static Map<String, Object> defaultInnerLogConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", DEFAULT_BACKEND);
        config.put("level", DEFAULT_LEVEL);
        config.put("structured_output_format", DEFAULT_STRUCTURED_OUTPUT_FORMAT);
        config.put("backup_count", DEFAULT_BACKUP_COUNT);
        config.put("max_bytes", DEFAULT_MAX_BYTES);
        config.put("format", DEFAULT_FORMAT);
        config.put("log_path", DEFAULT_LOG_PATH);
        config.put("log_file", DEFAULT_LOG_FILE);
        config.put("propagate", true);
        config.put("output", List.of("console", "file"));
        config.put("interface_log_file", DEFAULT_INTERFACE_LOG_FILE);
        config.put("interface_output", List.of("console", "file"));
        config.put("prompt_builder_interface_log_file", DEFAULT_PROMPT_BUILDER_INTERFACE_LOG_FILE);
        config.put("performance_log_file", DEFAULT_PERFORMANCE_LOG_FILE);
        config.put("performance_output", List.of("console", "file"));
        config.put("loggers", Map.of());
        return config;
    }

    public static Map<String, Object> defaultLogConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("logging", defaultInnerLogConfig());
        return config;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default Loguru backend configuration.
 *
 * <p>Mirrors Python's {@code DEFAULT_INNER_LOG_CONFIG} in
 * {@code openjiuwen.core.common.logging.loguru.constant}.</p>
 */
public final class LoguruConstants {

    private LoguruConstants() {
    }

    /**
     * Build the default Loguru backend config as mutable nested maps.
     */
    public static Map<String, Object> defaultInnerLogConfig() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("level", "INFO");
        defaults.put("enqueue", true);
        defaults.put("catch", false);
        defaults.put("backtrace", false);
        defaults.put("diagnose", false);

        Map<String, Object> console = new LinkedHashMap<>();
        console.put("target", "stdout");
        console.put("level", "INFO");
        console.put("serialize", false);
        console.put("colorize", true);
        console.put("enqueue", false);
        console.put("backtrace", true);
        console.put("diagnose", false);
        console.put("format",
            "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
                + "<magenta>{process.id}</magenta> | "
                + "<level>{level: <8}</level> | "
                + "<cyan>{extra[log_type]}</cyan> | "
                + "<yellow>{extra[trace_id]}</yellow> | "
                + "<blue>{extra[short_path]}:{line}</blue> | "
                + "{message}");

        Map<String, Object> appJson = new LinkedHashMap<>();
        appJson.put("target", "./logs/run/jiuwen.jsonl");
        appJson.put("level", "INFO");
        appJson.put("serialize", true);
        appJson.put("enqueue", true);
        appJson.put("rotation", "500 MB");
        appJson.put("retention", "14 days");
        appJson.put("compression", "gz");
        appJson.put("encoding", "utf-8");

        Map<String, Object> perfJson = new LinkedHashMap<>();
        perfJson.put("target", "./logs/performance/jiuwen_performance.jsonl");
        perfJson.put("level", "INFO");
        perfJson.put("serialize", true);
        perfJson.put("enqueue", true);
        perfJson.put("rotation", "200 MB");
        perfJson.put("retention", "7 days");
        perfJson.put("compression", "gz");
        perfJson.put("encoding", "utf-8");

        Map<String, Object> sinks = new LinkedHashMap<>();
        sinks.put("console", console);
        sinks.put("app_json", appJson);
        sinks.put("perf_json", perfJson);

        Map<String, Object> routes = new LinkedHashMap<>();
        routes.put("common", List.of("console", "app_json"));
        routes.put("interface", List.of("console", "app_json"));
        routes.put("prompt_builder", List.of("console", "app_json"));
        routes.put("performance", List.of("perf_json"));
        routes.put("*", List.of("console", "app_json"));

        Map<String, Object> commonLogger = new LinkedHashMap<>();
        commonLogger.put("level", "INFO");
        Map<String, Object> agentLogger = new LinkedHashMap<>();
        agentLogger.put("level", "INFO");
        Map<String, Object> loggers = new LinkedHashMap<>();
        loggers.put("common", commonLogger);
        loggers.put("agent", agentLogger);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", "loguru");
        config.put("defaults", defaults);
        config.put("sinks", sinks);
        config.put("routes", routes);
        config.put("loggers", loggers);
        return config;
    }
}

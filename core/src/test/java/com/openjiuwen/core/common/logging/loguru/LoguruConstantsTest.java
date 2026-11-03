package com.openjiuwen.core.common.logging.loguru;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LoguruConstantsTest {

    @Test
    void defaultInnerLogConfigMatchesPythonConstants() {
        Map<String, Object> config = LoguruConstants.defaultInnerLogConfig();

        assertEquals("loguru", config.get("backend"));
        assertInstanceOf(Map.class, config.get("defaults"));
        assertInstanceOf(Map.class, config.get("sinks"));
        assertInstanceOf(Map.class, config.get("routes"));
        assertInstanceOf(Map.class, config.get("loggers"));

        Map<?, ?> defaults = (Map<?, ?>) config.get("defaults");
        assertEquals("INFO", defaults.get("level"));
        assertEquals(true, defaults.get("enqueue"));
        assertEquals(false, defaults.get("catch"));
        assertEquals(false, defaults.get("backtrace"));
        assertEquals(false, defaults.get("diagnose"));

        Map<?, ?> sinks = (Map<?, ?>) config.get("sinks");
        Map<?, ?> console = (Map<?, ?>) sinks.get("console");
        assertEquals("stdout", console.get("target"));
        assertEquals(true, console.get("colorize"));
        assertEquals(false, console.get("serialize"));

        Map<?, ?> appJson = (Map<?, ?>) sinks.get("app_json");
        assertEquals("./logs/run/jiuwen.jsonl", appJson.get("target"));
        assertEquals("500 MB", appJson.get("rotation"));

        Map<?, ?> routes = (Map<?, ?>) config.get("routes");
        assertEquals(List.of("perf_json"), routes.get("performance"));
        assertEquals(List.of("console", "app_json"), routes.get("*"));
    }
}

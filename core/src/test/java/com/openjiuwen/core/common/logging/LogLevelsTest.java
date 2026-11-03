package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LogLevelsTest {

    @Test
    void normalizeLogLevelMatchesPythonSemantics() {
        assertEquals(LogLevels.WARNING, LogLevels.normalizeLogLevel(true, LogLevels.WARNING));
        assertEquals(LogLevels.ERROR, LogLevels.normalizeLogLevel(LogLevels.ERROR, LogLevels.WARNING));
        assertEquals(LogLevels.INFO, LogLevels.normalizeLogLevel("info", LogLevels.WARNING));
        assertEquals(LogLevels.WARNING, LogLevels.normalizeLogLevel("unknown", LogLevels.WARNING));
    }

    @Test
    void normalizeLoggingConfigDispatchesToDefaultBackendProvider() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("backend", "default");
        loggingConfig.put("level", "debug");
        loggingConfig.put("loggers", Map.of("common", Map.of("level", "error")));

        Map<String, Object> normalized = LogLevels.normalizeLoggingConfig(loggingConfig, LogLevels.WARNING);

        assertEquals("default", normalized.get("backend"));
        assertEquals(LogLevels.DEBUG, normalized.get("level"));
        assertInstanceOf(Map.class, normalized.get("loggers"));
        assertEquals(
            LogLevels.ERROR,
            ((Map<?, ?>) ((Map<?, ?>) normalized.get("loggers")).get("common")).get("level")
        );
    }

    @Test
    void normalizeLoggingConfigDispatchesToLoguruBackendProvider() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("backend", "loguru");
        loggingConfig.put("defaults", Map.of("level", "warning"));
        loggingConfig.put("sinks", Map.of("console", Map.of("target", "stdout")));
        loggingConfig.put("routes", Map.of("*", List.of("console")));

        Map<String, Object> normalized = LogLevels.normalizeLoggingConfig(loggingConfig, LogLevels.DEBUG);

        assertEquals("loguru", normalized.get("backend"));
        assertEquals(LogLevels.WARNING, normalized.get("level"));
        assertInstanceOf(Map.class, normalized.get("defaults"));
    }
}

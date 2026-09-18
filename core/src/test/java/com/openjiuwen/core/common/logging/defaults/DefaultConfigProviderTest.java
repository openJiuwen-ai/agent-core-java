package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LogLevels;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigProviderTest {

    @Test
    void normalizeDefaultLoggingConfigFallsBackToDefaultConstants() {
        Map<String, Object> normalized = DefaultConfigProvider.normalizeDefaultLoggingConfig("invalid");

        assertEquals(DefaultLogConstants.defaultInnerLogConfig(), normalized);
    }

    @Test
    void buildDefaultLoggerConfigAppliesDefaultsAndOverrides() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("level", "debug");
        loggingConfig.put("log_path", "target/test-logs/default-provider");
        loggingConfig.put("interface_output", List.of("file"));
        loggingConfig.put("interface_log_file", "interface/custom.log");
        loggingConfig.put("loggers", Map.of("interface", Map.of("level", "error")));

        Map<String, Object> config = DefaultConfigProvider.buildDefaultLoggerConfig(loggingConfig, "interface");

        assertEquals("default", config.get("backend"));
        assertEquals(List.of("file"), config.get("output"));
        assertEquals(LogLevels.ERROR, config.get("level"));
        assertTrue(String.valueOf(config.get("log_file")).replace('\\', '/').endsWith("/interface/custom.log"));
    }

    @Test
    void validateDefaultBackendConfigRejectsUnknownKeys() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("backend", "default");
        loggingConfig.put("unexpected", true);

        assertThrows(BaseError.class, () -> DefaultConfigProvider.validateDefaultBackendConfig(loggingConfig));
    }
}

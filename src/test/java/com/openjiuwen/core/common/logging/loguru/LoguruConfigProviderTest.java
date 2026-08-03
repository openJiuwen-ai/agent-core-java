package com.openjiuwen.core.common.logging.loguru;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LogLevels;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoguruConfigProviderTest {

    @Test
    void normalizeLoguruLoggingConfigMatchesPythonMergeBehavior() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("defaults", Map.of("level", "warning"));
        loggingConfig.put("sinks", Map.of("console", Map.of("target", "stdout", "serialize_mode", " EVENT ")));
        loggingConfig.put("routes", Map.of("*", List.of(" console ", "console")));
        loggingConfig.put("loggers", Map.of("worker", Map.of("level", "error")));

        Map<String, Object> normalized = LoguruConfigProvider.normalizeLoguruLoggingConfig(loggingConfig);

        assertEquals("loguru", normalized.get("backend"));
        assertEquals(LogLevels.WARNING, normalized.get("level"));
        assertEquals(
            "event",
            ((Map<?, ?>) ((Map<?, ?>) normalized.get("sinks")).get("console")).get("serialize_mode")
        );
        assertEquals(
            List.of("console", "console"),
            ((Map<?, ?>) normalized.get("routes")).get("*")
        );
    }

    @Test
    void buildLoguruLoggerConfigMaterializesTargets() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("defaults", Map.of("level", "info"));
        loggingConfig.put("sinks", Map.of("console", Map.of("target", "stdout")));
        loggingConfig.put("routes", Map.of("*", List.of("console")));

        Map<String, Object> config = LoguruConfigProvider.buildLoguruLoggerConfig(loggingConfig, "worker");

        assertEquals("loguru", config.get("backend"));
        assertEquals(LogLevels.INFO, config.get("level"));
        assertTrue(((List<?>) config.get("sinks")).size() == 1);
    }

    @Test
    void validateLoguruBackendConfigRejectsUnknownSinkReference() {
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("backend", "loguru");
        loggingConfig.put("sinks", Map.of("console", Map.of("target", "stdout")));
        loggingConfig.put("routes", Map.of("*", List.of("missing")));

        assertThrows(BaseError.class, () -> LoguruConfigProvider.validateLoguruBackendConfig(loggingConfig));
    }
}

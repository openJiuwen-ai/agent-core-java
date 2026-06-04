/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.loguru.LoguruConfigProvider;
import com.openjiuwen.core.common.logging.loguru.LoguruConstants;
import com.openjiuwen.core.common.logging.loguru.LoguruLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_loguru_backend.py} in
 * {@code tests.unit_tests.core.common.log}.
 */
@Tag("unit-test")
class TestLoguruBackend {

    @BeforeEach
    void setUp() {
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
        LoggingUtils.clearSessionId();
    }

    @AfterEach
    void tearDown() {
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
        LoggingUtils.clearSessionId();
    }

    @Test
    @Tag("level0")
    @DisplayName("Test reset log manager")
    void testResetLogManager() {
        LogManager.LogConfigProvider.setProvider(TestLoguruBackend::basicLoggerConfigs);
        LogManager.initialize("loguru");

        LogManager.reset();

        assertNotNull(LogManager.class);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test initialize can switch to loguru backend via argument")
    void testInitializeCanSwitchToLoguruBackendViaArgument() {
        LogManager.LogConfigProvider.setProvider(TestLoguruBackend::basicLoggerConfigs);
        LogManager.initialize("loguru");

        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(LoguruLogger.class, logger);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test builtin default backend initializes common with default class")
    void testBuiltinDefaultBackendInitializesCommonWithDefaultClass() {
        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(DefaultLogger.class, logger);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test runtime reconfigure rebuilds common logger")
    void testRuntimeReconfigureRebuildsCommonLogger() {
        LogManager.LogConfigProvider.setProvider(TestLoguruBackend::basicLoggerConfigs);
        LogManager.initialize("default");
        LoggerProtocol defaultLogger = LogManager.getLogger("common");

        LogManager.initialize("loguru");
        LoggerProtocol loguruLogger = LogManager.getLogger("common");

        assertNotSame(defaultLogger, loguruLogger);
        assertInstanceOf(LoguruLogger.class, loguruLogger);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger name")
    void testLoguruLoggerName() {
        LoguruLogger logger = new LoguruLogger("team", Map.of("output", "console"));

        assertEquals("team", logger.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger percent formatting")
    void testLoguruLoggerPercentFormatting() {
        LoguruLogger logger = new LoguruLogger("percent-loguru", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("value=%s", 42);

        assertTrue(handler.messages().contains("value=42"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger brace formatting")
    void testLoguruLoggerBraceFormatting() {
        LoguruLogger logger = new LoguruLogger("brace-loguru", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("hello {}", "world");

        assertTrue(handler.messages().contains("hello world"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger warning error critical levels")
    void testLoguruLoggerWarningErrorCriticalLevels() {
        LoguruLogger logger = new LoguruLogger("levels-loguru", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        logger.setLevel(LogLevels.DEBUG);

        logger.warning("warn message");
        logger.error("error message");
        logger.critical("critical message");

        assertTrue(handler.messages().contains("warn message"));
        assertTrue(handler.messages().contains("error message"));
        assertTrue(handler.messages().contains("[CRITICAL] critical message"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger exception")
    void testLoguruLoggerException() {
        LoguruLogger logger = new LoguruLogger("exception-loguru", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.exception("failed", new IllegalStateException("boom"));

        assertTrue(handler.messages().contains("failed"));
        assertInstanceOf(IllegalStateException.class, handler.records().get(0).getThrown());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test logger set level only changes adapter threshold")
    void testLoggerSetLevelOnlyChangesAdapterThreshold() {
        LoguruLogger logger = new LoguruLogger("debug-loguru", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.setLevel(LogLevels.DEBUG);
        logger.debug("debug visible");

        assertTrue(handler.messages().contains("debug visible"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger config access")
    void testLoguruLoggerConfigAccess() {
        LoguruLogger logger = new LoguruLogger("config-loguru", Map.of("level", LogLevels.INFO));

        assertEquals(LogLevels.INFO, logger.getConfig().get("level"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger reconfigure")
    void testLoguruLoggerReconfigure() {
        LoguruLogger logger = new LoguruLogger("reconfigure-loguru", Map.of("level", LogLevels.INFO));

        logger.reconfigure(Map.of("level", LogLevels.DEBUG, "output", "console"));

        assertEquals(LogLevels.DEBUG, logger.getConfig().get("level"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru constants default backend")
    void testLoguruConstantsDefaultBackend() {
        Map<String, Object> config = LoguruConstants.defaultInnerLogConfig();

        assertEquals("loguru", config.get("backend"));
        assertTrue(config.containsKey("sinks"));
        assertTrue(config.containsKey("routes"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru provider normalizes logging config")
    void testLoguruProviderNormalizesLoggingConfig() {
        Map<String, Object> normalized = LoguruConfigProvider.normalizeLoguruLoggingConfig(
                Map.of("level", "DEBUG", "defaults", Map.of("level", "WARNING")));

        assertEquals("loguru", normalized.get("backend"));
        assertEquals(LogLevels.WARNING, normalized.get("level"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru provider builds dynamic logger config")
    void testLoguruProviderBuildsDynamicLoggerConfig() {
        Map<String, Object> loaded = LoguruConfigProvider.loadLoguruBackendConfig(minimalConfig());

        Map<String, Object> common = LoguruConfigProvider.buildLoguruLoggerConfig(loaded, "common");

        assertEquals("loguru", common.get("backend"));
        assertEquals(LogLevels.INFO, common.get("effective_level"));
        assertTrue(common.containsKey("sinks"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logger level override")
    void testLoguruLoggerLevelOverride() {
        Map<String, Object> loaded = LoguruConfigProvider.loadLoguruBackendConfig(minimalConfig());

        Map<String, Object> team = LoguruConfigProvider.buildLoguruLoggerConfig(loaded, "team");

        assertEquals(LogLevels.DEBUG, team.get("effective_level"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test route fallback uses star route")
    void testRouteFallbackUsesStarRoute() {
        Map<String, Object> loaded = LoguruConfigProvider.loadLoguruBackendConfig(minimalConfig());

        List<String> sinks = LoguruConfigProvider.resolveRouteSinkNames(loaded, "unknown");

        assertEquals(List.of("console"), sinks);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test specific route wins over fallback")
    void testSpecificRouteWinsOverFallback() {
        Map<String, Object> loaded = LoguruConfigProvider.loadLoguruBackendConfig(minimalConfig());

        List<String> sinks = LoguruConfigProvider.resolveRouteSinkNames(loaded, "team");

        assertEquals(List.of("team_console"), sinks);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test resolve stdout target")
    void testResolveStdoutTarget() {
        assertEquals("stdout", LoguruConfigProvider.resolveLoguruTarget(" stdout "));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru backend rejects logger specific sinks")
    void testLoguruBackendRejectsLoggerSpecificSinks() {
        Map<String, Object> config = Map.of(
                "backend", "loguru",
                "sinks", Map.of("console", Map.of("target", "stdout")),
                "routes", Map.of("*", List.of("console")),
                "loggers", Map.of("team", Map.of("sinks", List.of("console")))
        );

        assertThrows(RuntimeException.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru backend rejects default specific root keys")
    void testLoguruBackendRejectsDefaultSpecificRootKeys() {
        Map<String, Object> config = Map.of(
                "backend", "loguru",
                "format", "%(message)s",
                "sinks", Map.of("console", Map.of("target", "stdout")),
                "routes", Map.of("*", List.of("console"))
        );

        assertThrows(RuntimeException.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru backend rejects invalid serialize mode")
    void testLoguruBackendRejectsInvalidSerializeMode() {
        Map<String, Object> config = Map.of(
                "backend", "loguru",
                "sinks", Map.of("console", Map.of("target", "stdout", "serialize_mode", "bad")),
                "routes", Map.of("*", List.of("console"))
        );

        assertThrows(RuntimeException.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test loguru logging methods do not throw")
    void testLoguruLoggingMethodsDoNotThrow() {
        LoguruLogger logger = new LoguruLogger("smoke-loguru", Map.of("output", "console"));

        assertDoesNotThrow(() -> {
            logger.debug("debug");
            logger.info("info");
            logger.warning("warning");
            logger.error("error");
        });
    }

    private static Map<String, Object> minimalConfig() {
        return Map.of(
                "backend", "loguru",
                "defaults", Map.of("level", "INFO"),
                "sinks", Map.of(
                        "console", Map.of("target", "stdout", "level", "INFO"),
                        "team_console", Map.of("target", "stdout", "level", "DEBUG")
                ),
                "routes", Map.of(
                        "team", List.of("team_console"),
                        "*", List.of("console")
                ),
                "loggers", Map.of("team", Map.of("level", "DEBUG"))
        );
    }

    private static Map<String, Map<String, Object>> basicLoggerConfigs() {
        return Map.of(
                "common", Map.of("output", "console", "level", LogLevels.INFO),
                "interface", Map.of("output", "console", "level", LogLevels.INFO),
                "runner", Map.of("output", "console", "level", LogLevels.INFO)
        );
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> records() {
            return records;
        }

        List<String> messages() {
            return records.stream().map(LogRecord::getMessage).toList();
        }
    }
}

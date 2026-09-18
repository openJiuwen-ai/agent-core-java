/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoggingTest {

    @AfterEach
    void tearDown() {
        LoggingDefaults.reset();
        LogManager.reset();
        LogManager.LogConfigProvider.setProvider(null);
    }

    @Test
    void defaultLoggerSanitizesCriticalMessagesAndPublishesJulRecord() {
        DefaultLogger logger = new DefaultLogger("worker", Map.of("output", "console", "level", LogLevels.INFO));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        logger.critical("line1\nline2");

        assertNotNull(handler.lastRecord);
        assertTrue(handler.lastRecord.getMessage().contains("[CRITICAL]"));
        assertTrue(handler.lastRecord.getMessage().contains("\\n"));
    }

    @Test
    void defaultLoggerLogPublishesCriticalWhenThresholdIsCritical() {
        DefaultLogger logger = new DefaultLogger("critical-threshold",
                Map.of("output", "console", "level", LogLevels.CRITICAL));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        logger.log(LogLevels.CRITICAL, "visible critical");

        assertNotNull(handler.lastRecord);
        assertTrue(handler.lastRecord.getMessage().contains("visible critical"));
    }

    @Test
    void defaultLoggerLogEventPublishesStructuredCriticalWhenThresholdIsCritical() {
        DefaultLogger logger = new DefaultLogger("structured-critical-threshold",
                Map.of("output", "console", "level", LogLevels.CRITICAL));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        BaseLogEvent event = new BaseLogEvent();
        event.setLogLevel(LogLevel.CRITICAL);
        event.setEventType(LogEventType.AGENT_START);

        logger.logEvent("visible structured critical", LogEventType.AGENT_START, event);

        assertNotNull(handler.lastRecord);
        assertTrue(handler.lastRecord.getMessage().contains("\"log_level\":\"CRITICAL\""));
        assertTrue(handler.lastRecord.getMessage().contains("visible structured critical"));
    }

    @Test
    void defaultLoggerFiltersMessagesBelowConfiguredThreshold() {
        DefaultLogger logger = new DefaultLogger("threshold", Map.of("output", "console", "level", LogLevels.ERROR));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        logger.info("hidden info");
        assertFalse(handler.hasRecord(), "INFO must be filtered when threshold is ERROR");

        logger.error("visible error");
        assertTrue(handler.hasRecord(), "ERROR must be published when threshold is ERROR");
        assertTrue(handler.lastRecord.getMessage().contains("visible error"));
    }

    @Test
    void defaultLoggerReconfigureUpdatesThresholdWithoutBackendSpecificLogger() {
        DefaultLogger logger = new DefaultLogger("reconfigure-threshold",
                Map.of("output", "console", "level", LogLevels.ERROR));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        logger.info("hidden before reconfigure");
        assertFalse(handler.hasRecord());

        logger.reconfigure(Map.of("output", "console", "level", LogLevels.INFO));
        logger.info("visible after reconfigure");

        assertTrue(handler.hasRecord());
        assertTrue(handler.lastRecord.getMessage().contains("visible after reconfigure"));
    }

    @Test
    void defaultLoggerCreatesStructuredEventJsonAndLoguruAdapterDelegates() throws Exception {
        DefaultLogger logger = new DefaultLogger("worker", Map.of("output", "console", "level", LogLevels.INFO));
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        logger.logEvent("hello", LogEventType.AGENT_START, null);

        assertNotNull(handler.lastRecord);
        assertTrue(handler.lastRecord.getMessage().contains("\"event_type\":\"agent_start\""));

        com.openjiuwen.core.common.logging.loguru.LoguruLogger loguruLogger =
                new com.openjiuwen.core.common.logging.loguru.LoguruLogger("worker", Map.of("output", "console"));
        assertEquals("worker", loguruLogger.getName());
        assertEquals("console", String.valueOf(loguruLogger.getConfig().get("output")));
        assertInstanceOf(java.util.logging.Logger.class, loguruLogger.logger());
    }

    @Test
    void configureLogConfigResetsManagerAndUpdatesSnapshot() throws Exception {
        Path logDir = Files.createTempDirectory("logging-defaults");
        Map<String, Object> loggingConfig = new LinkedHashMap<>();
        loggingConfig.put("backend", "default");
        loggingConfig.put("level", "DEBUG");
        loggingConfig.put("output", List.of("console"));
        loggingConfig.put("log_file", logDir.resolve("app.log").toString());

        LoggingDefaults.configureLogConfig(loggingConfig);

        Map<String, Object> snapshot = LoggingDefaults.getLogConfigSnapshot();
        assertEquals("default", snapshot.get("backend"));
        assertFalse(LogManager.getAllLoggers().isEmpty());
    }

    private static final class CapturingHandler extends Handler {
        private LogRecord lastRecord;

        @Override
        public void publish(LogRecord record) {
            this.lastRecord = record;
        }

        boolean hasRecord() {
            return lastRecord != null;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

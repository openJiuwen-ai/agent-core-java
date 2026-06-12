/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.common.logging.events.LogEventType;
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

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

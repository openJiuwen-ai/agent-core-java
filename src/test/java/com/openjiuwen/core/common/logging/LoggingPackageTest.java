/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoggingPackageTest {

    @AfterEach
    void tearDown() {
        LogManager.reset();
        LogManager.LogConfigProvider.setProvider(null);
    }

    @Test
    void lazyLoggerRebindsWhenGetterReturnsNewLogger() {
        RecordingLogger first = new RecordingLogger("first");
        RecordingLogger second = new RecordingLogger("second");
        AtomicReference<LoggerProtocol> current = new AtomicReference<>(first);
        LazyLogger lazyLogger = new LazyLogger(current::get);

        lazyLogger.info("one");
        current.set(second);
        lazyLogger.info("two");

        assertEquals(List.of("one"), first.infoMessages);
        assertEquals(List.of("two"), second.infoMessages);
    }

    @Test
    void commonLoggerRebindsAfterManagerReset() {
        AtomicReference<RecordingLogger> createdLogger = new AtomicReference<>();
        LogManager.LogConfigProvider.setProvider(() -> Map.of("common", Map.of("output", "console", "level", LogLevels.INFO)));
        LogManager.setDefaultLoggerFactory((logType, config) -> {
            RecordingLogger logger = new RecordingLogger(logType);
            createdLogger.set(logger);
            return logger;
        });

        Loggers.COMMON.info("before-reset");
        RecordingLogger first = createdLogger.get();

        LogManager.reset();
        LogManager.LogConfigProvider.setProvider(() -> Map.of("common", Map.of("output", "console", "level", LogLevels.INFO)));
        LogManager.setDefaultLoggerFactory((logType, config) -> {
            RecordingLogger logger = new RecordingLogger(logType + "-reset");
            createdLogger.set(logger);
            return logger;
        });

        Loggers.COMMON.info("after-reset");
        RecordingLogger second = createdLogger.get();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(List.of("before-reset"), first.infoMessages);
        assertEquals(List.of("after-reset"), second.infoMessages);
    }

    @Test
    void configManagerReexportsLevelHelpers() {
        assertEquals(LogLevels.INFO, ConfigManager.INFO);
        assertEquals(LogLevels.WARNING, ConfigManager.normalizeLogLevel("warning"));
        assertEquals("loguru", ConfigManager.extractBackend(Map.of("backend", "loguru")));
        assertEquals(
                LogLevels.DEBUG,
                ConfigManager.normalizeLogLevel(
                        ConfigManager.normalizeLoggingConfig(Map.of("level", "debug")).get("level")
                )
        );
        assertSame(LogLevels.NAME_TO_LEVEL, ConfigManager.NAME_TO_LEVEL);
    }

    private static final class RecordingLogger implements LoggerProtocol {
        private final String name;
        private final List<String> infoMessages = new ArrayList<>();
        private final Map<String, Object> config = new LinkedHashMap<>();

        private RecordingLogger(String name) {
            this.name = name;
        }

        @Override
        public void debug(String msg, Object... args) {
        }

        @Override
        public void info(String msg, Object... args) {
            infoMessages.add(msg);
        }

        @Override
        public void warning(String msg, Object... args) {
        }

        @Override
        public void error(String msg, Object... args) {
        }

        @Override
        public void critical(String msg, Object... args) {
        }

        @Override
        public void exception(String msg, Throwable t, Object... args) {
        }

        @Override
        public void log(int level, String msg, Object... args) {
        }

        @Override
        public void setLevel(int level) {
        }

        @Override
        public Map<String, Object> getConfig() {
            return config;
        }

        @Override
        public void reconfigure(Map<String, Object> config) {
            this.config.clear();
            this.config.putAll(config);
        }
    }
}

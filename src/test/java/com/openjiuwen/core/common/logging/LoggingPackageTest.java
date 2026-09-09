/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoggingPackageTest {

    @AfterEach
    void tearDown() {
        LogManager.reset();
        LogManager.LogConfigProvider.setProvider(null);
        LoggingDefaults.reset();
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
        // This suite runs in a reused single-fork JVM where components from
        // earlier classes may still have live background threads that log.
        // A stray LogManager.initialize()/getLogger() landing between reset()
        // and our factory setup would pre-register "common" as a DefaultLogger
        // and make this test flaky. Isolate with a unique log type nobody else
        // requests, and set the factory before the provider so any stray
        // initialization can never create OUR type with the wrong factory.
        String logType = "logging-package-test-common";
        LogManager.reset();
        // Track by logType: initialize may create multiple loggers; "last created" is flaky.
        Map<String, RecordingLogger> byType = new LinkedHashMap<>();
        LogManager.setDefaultLoggerFactory((type, config) -> {
            RecordingLogger logger = new RecordingLogger(type);
            byType.put(type, logger);
            return logger;
        });
        LogManager.LogConfigProvider.setProvider(
                () -> Map.of(logType, Map.of("output", "console", "level", LogLevels.INFO)));

        LoggerProtocol lazyCommon = new LazyLogger(() -> LogManager.getLogger(logType));
        lazyCommon.info("before-reset");
        RecordingLogger first = assertInstanceOf(RecordingLogger.class, LogManager.getLogger(logType));
        assertSame(byType.get(logType), first);
        assertEquals(List.of("before-reset"), first.infoMessages);

        LogManager.reset();
        byType.clear();
        LogManager.setDefaultLoggerFactory((type, config) -> {
            RecordingLogger logger = new RecordingLogger(type + "-reset");
            byType.put(type, logger);
            return logger;
        });
        LogManager.LogConfigProvider.setProvider(
                () -> Map.of(logType, Map.of("output", "console", "level", LogLevels.INFO)));

        lazyCommon.info("after-reset");
        RecordingLogger second = assertInstanceOf(RecordingLogger.class, LogManager.getLogger(logType));
        assertSame(byType.get(logType), second);
        assertNotSame(first, second);
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

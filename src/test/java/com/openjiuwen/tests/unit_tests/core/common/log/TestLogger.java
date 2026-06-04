/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.common.log;

import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_logger.py} in
 * {@code tests.unit_tests.core.common.log}.
 */
@Tag("unit-test")
class TestLogger {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
        LoggingUtils.clearSessionId();
        LoggingUtils.clearMemberId();
    }

    @AfterEach
    void tearDown() {
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
        LoggingUtils.clearSessionId();
        LoggingUtils.clearMemberId();
    }

    @Test
    @Tag("level1")
    @DisplayName("Test thread trace id isolation")
    void testThreadTraceIdIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        List<String> seen = new CopyOnWriteArrayList<>();

        for (int i = 1; i <= 3; i++) {
            String traceId = "trace-" + i;
            executor.submit(() -> {
                LoggingUtils.setSessionId(traceId);
                seen.add(LoggingUtils.getSessionId());
                ready.countDown();
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertTrue(seen.containsAll(List.of("trace-1", "trace-2", "trace-3")));
        assertEquals("default_trace_id", LoggingUtils.getSessionId());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test default logger creation")
    void testDefaultLoggerCreation() {
        LogManager.initialize();

        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(DefaultLogger.class, logger);
    }

    @Test
    @Tag("level1")
    @DisplayName("Test custom logger registration and usage")
    void testCustomLoggerRegistrationAndUsage() {
        RecordingLogger logger = new RecordingLogger();

        LogManager.registerLogger("custom", logger);
        LogManager.getLogger("custom").info("hello {}", "world");

        assertEquals(List.of("INFO: hello {}"), logger.messages());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test get all loggers")
    void testGetAllLoggers() {
        LogManager.initialize();
        LogManager.getLogger("common");
        LogManager.getLogger("interface");

        Map<String, LoggerProtocol> loggers = LogManager.getAllLoggers();

        assertTrue(loggers.containsKey("common"));
        assertTrue(loggers.containsKey("interface"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test register logger type check")
    void testRegisterLoggerTypeCheck() {
        LogManager.TypeError thrown = assertThrows(LogManager.TypeError.class,
                () -> LogManager.registerLogger("bad", null));

        assertTrue(thrown.getMessage().contains("Logger must implement LoggerProtocol"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test get logger creates on demand")
    void testGetLoggerCreatesOnDemand() {
        LogManager.initialize();

        LoggerProtocol first = LogManager.getLogger("new_type");
        LoggerProtocol second = LogManager.getLogger("new_type");

        assertSame(first, second);
        assertInstanceOf(DefaultLogger.class, first);
    }

    @Test
    @Tag("level1")
    @DisplayName("Test default logger factory override")
    void testDefaultLoggerFactoryOverride() {
        LogManager.setDefaultLoggerFactory((name, config) -> new RecordingLogger());

        LoggerProtocol logger = LogManager.getLogger("factory-test");

        assertInstanceOf(RecordingLogger.class, logger);
    }

    @Test
    @Tag("level1")
    @DisplayName("Test initialize is idempotent for same backend")
    void testInitializeIsIdempotentForSameBackend() {
        LogManager.initialize("default");
        LoggerProtocol before = LogManager.getLogger("common");

        LogManager.initialize("default");

        assertSame(before, LogManager.getLogger("common"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test reset clears loggers")
    void testResetClearsLoggers() {
        LogManager.initialize();
        LoggerProtocol before = LogManager.getLogger("common");

        LogManager.reset();
        LoggerProtocol after = LogManager.getLogger("common");

        assertNotSame(before, after);
        assertInstanceOf(DefaultLogger.class, after);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test default session id")
    void testDefaultSessionId() {
        assertEquals("default_trace_id", LoggingUtils.getSessionId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test set session id")
    void testSetSessionId() {
        LoggingUtils.setSessionId("session-001");

        assertEquals("session-001", LoggingUtils.getSessionId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test clear session id")
    void testClearSessionId() {
        LoggingUtils.setSessionId("session-001");

        LoggingUtils.clearSessionId();

        assertEquals("default_trace_id", LoggingUtils.getSessionId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test null session id defaults")
    void testNullSessionIdDefaults() {
        LoggingUtils.setSessionId(null);

        assertEquals("default_trace_id", LoggingUtils.getSessionId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test default member id")
    void testDefaultMemberId() {
        assertEquals("", LoggingUtils.getMemberId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test set member id")
    void testSetMemberId() {
        LoggingUtils.setMemberId("member-1");

        assertEquals("member-1", LoggingUtils.getMemberId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test clear member id")
    void testClearMemberId() {
        LoggingUtils.setMemberId("member-1");

        LoggingUtils.clearMemberId();

        assertEquals("", LoggingUtils.getMemberId());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test log max bytes valid value")
    void testLogMaxBytesValidValue() {
        assertEquals(1024, LoggingUtils.getLogMaxBytes("1024"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test log max bytes caps invalid ranges")
    void testLogMaxBytesCapsInvalidRanges() {
        int defaultMaxBytes = 100 * 1024 * 1024;

        assertEquals(defaultMaxBytes, LoggingUtils.getLogMaxBytes("-1"));
        assertEquals(defaultMaxBytes, LoggingUtils.getLogMaxBytes(String.valueOf(defaultMaxBytes + 1)));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test invalid log max bytes raises")
    void testInvalidLogMaxBytesRaises() {
        assertThrows(IllegalArgumentException.class, () -> LoggingUtils.getLogMaxBytes("not-a-number"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test resolve log type label")
    void testResolveLogTypeLabel() {
        assertEquals("perf", LoggingUtils.resolveLogTypeLabel("performance"));
        assertEquals("common", LoggingUtils.resolveLogTypeLabel("common"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test format log filename pattern")
    void testFormatLogFilenamePattern() {
        String formatted = LoggingUtils.formatLogFilename("app.log", "{name}-{pid}{ext}");

        assertTrue(formatted.startsWith("app-"));
        assertTrue(formatted.endsWith(".log"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test normalize log level string")
    void testNormalizeLogLevelString() {
        assertEquals(LogLevels.INFO, LogLevels.normalizeLogLevel("INFO"));
        assertEquals(LogLevels.DEBUG, LogLevels.normalizeLogLevel("debug"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test normalize log level fallback")
    void testNormalizeLogLevelFallback() {
        assertEquals(LogLevels.ERROR, LogLevels.normalizeLogLevel("bad", LogLevels.ERROR));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test extract backend default")
    void testExtractBackendDefault() {
        assertEquals("default", LogLevels.extractBackend(Map.of()));
        assertEquals("loguru", LogLevels.extractBackend(Map.of("backend", " loguru ")));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test normalize logging config")
    void testNormalizeLoggingConfig() {
        Map<String, Object> normalized = LogLevels.normalizeLoggingConfig(Map.of("level", "DEBUG"));

        assertEquals(LogLevels.DEBUG, normalized.get("level"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test logger config access")
    void testLoggerConfigAccess() {
        DefaultLogger logger = new DefaultLogger("config-test", Map.of("level", LogLevels.INFO, "output", "console"));

        assertEquals(LogLevels.INFO, logger.getConfig().get("level"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test logger reconfigure")
    void testLoggerReconfigure() {
        DefaultLogger logger = new DefaultLogger("reconfigure-test", Map.of("level", LogLevels.INFO));

        logger.reconfigure(Map.of("level", LogLevels.DEBUG, "output", "console"));

        assertEquals(LogLevels.DEBUG, logger.getConfig().get("level"));
        assertEquals("console", logger.getConfig().get("output"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test default logger percent formatting")
    void testDefaultLoggerPercentFormatting() {
        DefaultLogger logger = new DefaultLogger("percent-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("value=%s", 42);

        assertTrue(handler.messages().contains("value=42"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test default logger brace formatting")
    void testDefaultLoggerBraceFormatting() {
        DefaultLogger logger = new DefaultLogger("brace-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("hello {}", "world");

        assertTrue(handler.messages().contains("hello world"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test message sanitization")
    void testMessageSanitization() {
        DefaultLogger logger = new DefaultLogger("sanitize-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("line1\nline2\tend");

        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("line1\\nline2\\tend")));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test filter suppresses log record")
    void testFilterSuppressesLogRecord() {
        DefaultLogger logger = new DefaultLogger("filter-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        Filter blockAll = record -> false;
        logger.addHandler(handler);
        logger.addFilter(blockAll);

        logger.info("blocked");

        assertFalse(handler.messages().contains("blocked"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test remove filter restores log record")
    void testRemoveFilterRestoresLogRecord() {
        DefaultLogger logger = new DefaultLogger("remove-filter-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        Filter blockAll = record -> false;
        logger.addHandler(handler);
        logger.addFilter(blockAll);
        logger.removeFilter(blockAll);

        logger.info("allowed");

        assertTrue(handler.messages().contains("allowed"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test all log levels")
    void testAllLogLevels() {
        DefaultLogger logger = new DefaultLogger("levels-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        logger.setLevel(LogLevels.DEBUG);

        logger.debug("debug message");
        logger.info("info message");
        logger.warning("warning message");
        logger.error("error message");
        logger.critical("critical message");

        assertTrue(handler.messages().contains("debug message"));
        assertTrue(handler.messages().contains("info message"));
        assertTrue(handler.messages().contains("warning message"));
        assertTrue(handler.messages().contains("error message"));
        assertTrue(handler.messages().contains("[CRITICAL] critical message"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test log level filtering")
    void testLogLevelFiltering() {
        DefaultLogger logger = new DefaultLogger("level-filter-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        logger.setLevel(LogLevels.ERROR);

        logger.info("hidden info");
        logger.error("visible error");

        assertFalse(handler.messages().contains("hidden info"));
        assertTrue(handler.messages().contains("visible error"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test exception logging")
    void testExceptionLogging() {
        DefaultLogger logger = new DefaultLogger("exception-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.exception("Exception occurred", new IllegalArgumentException("bad input"));

        assertTrue(handler.messages().contains("Exception occurred"));
        assertInstanceOf(IllegalArgumentException.class, handler.records().get(0).getThrown());
    }

    @Test
    @Tag("level2")
    @DisplayName("Test create nested log directory")
    void testCreateNestedLogDirectory() {
        Path logFile = tempDir.resolve("logs").resolve("run").resolve("test.log");

        new DefaultLogger("nested-dir-test", Map.of("output", List.of("file"), "log_file", logFile.toString()));

        assertTrue(Files.isDirectory(logFile.getParent()));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test create existing directory no error")
    void testCreateExistingDirectoryNoError() throws IOException {
        Path dir = tempDir.resolve("logs").resolve("existing");
        Files.createDirectories(dir);
        Path logFile = dir.resolve("test.log");

        assertDoesNotThrow(() -> new DefaultLogger("existing-dir-test",
                Map.of("output", List.of("file"), "log_file", logFile.toString())));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test normalize and validate log path")
    void testNormalizeAndValidateLogPath() throws IOException {
        Path logFile = tempDir.resolve("valid.log");
        Files.createFile(logFile);

        String normalized = LoggingUtils.normalizeAndValidateLogPath(logFile);

        assertTrue(normalized.endsWith("valid.log"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test logger inner JUL object")
    void testLoggerInnerJulObject() {
        DefaultLogger logger = new DefaultLogger("jul-test", Map.of("output", "console"));

        assertNotNull(logger.logger());
        assertTrue(logger.logger().getName().contains("jul-test"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test logging context is cleared after publish")
    void testLoggingContextIsClearedAfterPublish() {
        DefaultLogger logger = new DefaultLogger("context-clear-test", Map.of("output", "console"));
        LoggingUtils.setSessionId("trace-ctx");

        logger.info("context message");

        assertEquals("trace-ctx", LoggingUtils.getSessionId());
        assertFalse(org.slf4j.MDC.getCopyOfContextMap() != null
                && org.slf4j.MDC.getCopyOfContextMap().containsKey("trace_id"));
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

    private static final class RecordingLogger implements LoggerProtocol {
        private final List<String> messages = new ArrayList<>();
        private final AtomicReference<Map<String, Object>> config = new AtomicReference<>(Map.of());

        @Override
        public void debug(String msg, Object... args) {
            messages.add("DEBUG: " + msg);
        }

        @Override
        public void info(String msg, Object... args) {
            messages.add("INFO: " + msg);
        }

        @Override
        public void warning(String msg, Object... args) {
            messages.add("WARNING: " + msg);
        }

        @Override
        public void error(String msg, Object... args) {
            messages.add("ERROR: " + msg);
        }

        @Override
        public void critical(String msg, Object... args) {
            messages.add("CRITICAL: " + msg);
        }

        @Override
        public void exception(String msg, Throwable t, Object... args) {
            messages.add("EXCEPTION: " + msg + ":" + t.getMessage());
        }

        @Override
        public void log(int level, String msg, Object... args) {
            messages.add("LOG(" + level + "): " + msg);
        }

        @Override
        public void setLevel(int level) {
            config.set(Map.of("level", level));
        }

        @Override
        public Map<String, Object> getConfig() {
            return config.get();
        }

        @Override
        public void reconfigure(Map<String, Object> newConfig) {
            config.set(newConfig == null ? Map.of() : Map.copyOf(newConfig));
        }

        List<String> messages() {
            return messages;
        }
    }
}

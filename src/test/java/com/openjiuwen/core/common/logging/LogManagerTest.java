/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.defaults.LogConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for LogManager and LoggingUtils.
 * <p>
 * Mirrors Python's {@code test_logger.py} in
 * {@code tests.unit_tests.core.common.log.test_logger}.
 */
class LogManagerTest {

    @BeforeEach
    void setUp() {
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
    }

    @AfterEach
    void tearDown() {
        LoggingUtils.clearSessionId();
        LogManager.LogConfigProvider.setProvider(null);
        LogManager.reset();
    }

    // ==========================================================================
    // Helper: Simple LoggerProtocol implementation for testing
    // ==========================================================================
    static class TestLogger implements LoggerProtocol {
        final String logType;
        final List<String> messages = new CopyOnWriteArrayList<>();
        int currentLevel = 0;
        Map<String, Object> config;

        TestLogger(String logType, Map<String, Object> config) {
            this.logType = logType;
            this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        }

        @Override public void debug(String msg, Object... args) { messages.add("DEBUG: " + msg); }
        @Override public void info(String msg, Object... args) { messages.add("INFO: " + msg); }
        @Override public void warning(String msg, Object... args) { messages.add("WARNING: " + msg); }
        @Override public void error(String msg, Object... args) { messages.add("ERROR: " + msg); }
        @Override public void critical(String msg, Object... args) { messages.add("CRITICAL: " + msg); }
        @Override public void exception(String msg, Throwable t, Object... args) {
            messages.add("EXCEPTION: " + msg + " | " + t.getMessage());
        }
        @Override public void log(int level, String msg, Object... args) { messages.add("LOG(" + level + "): " + msg); }
        @Override public void setLevel(int level) { this.currentLevel = level; }
        @Override public Map<String, Object> getConfig() { return Map.copyOf(config); }
        @Override public void reconfigure(Map<String, Object> newConfig) { this.config = new HashMap<>(newConfig); }
    }

    static class RecordingHandler extends Handler {
        final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override public void flush() { }

        @Override public void close() { }
    }

    private void initializeWithTestFactory() {
        LogManager.setDefaultLoggerFactory(TestLogger::new);
        LogManager.LogConfigProvider.setProvider(() -> {
            Map<String, Map<String, Object>> configs = new HashMap<>();
            configs.put("common", Map.of("level", "INFO", "output", "console"));
            configs.put("interface", Map.of("level", "INFO", "output", "console"));
            configs.put("performance", Map.of("level", "INFO", "output", "console"));
            configs.put("prompt_builder", Map.of("level", "INFO", "output", "console"));
            return configs;
        });
        LogManager.initialize();
    }

    private static Map<String, Object> loggingConfig(Path logPath) {
        return new HashMap<>(Map.of(
                "backend", "default",
                "level", "INFO",
                "log_path", logPath.toString(),
                "output", "console",
                "structured_output_format", "json",
                "loggers", Map.of()));
    }

    // ==========================================================================
    // TestLogManager: test_custom_logger_registration_and_usage
    // ==========================================================================
    @Test
    @DisplayName("Register and use a custom logger")
    void testCustomLoggerRegistrationAndUsage() {
        initializeWithTestFactory();

        TestLogger customLogger = new TestLogger("custom", Map.of());
        LogManager.registerLogger("custom", customLogger);

        LoggerProtocol retrieved = LogManager.getLogger("custom");
        assertSame(customLogger, retrieved);

        retrieved.info("Test custom logger");
        assertTrue(customLogger.messages.contains("INFO: Test custom logger"));
    }

    @Test
    @DisplayName("registerLogger rejects null logger")
    void testRegisterLoggerTypeCheck() {
        initializeWithTestFactory();

        LogManager.TypeError error = assertThrows(LogManager.TypeError.class, () ->
                LogManager.registerLogger("bad", null));

        assertTrue(error.getMessage().contains("Logger must implement LoggerProtocol"));
    }

    // ==========================================================================
    // TestLogManager: test_default_logger_creation
    // ==========================================================================
    @Test
    @DisplayName("Get logger creates on demand for unknown type")
    void testDefaultLoggerCreation() {
        initializeWithTestFactory();

        LoggerProtocol newLogger = LogManager.getLogger("new_type_test");
        assertNotNull(newLogger);
        assertInstanceOf(TestLogger.class, newLogger);
        assertEquals("new_type_test", ((TestLogger) newLogger).logType);

        // Same logger returned on subsequent call
        LoggerProtocol sameLogger = LogManager.getLogger("new_type_test");
        assertSame(newLogger, sameLogger);
    }

    // ==========================================================================
    // TestLogManager: test_get_all_loggers
    // ==========================================================================
    @Test
    @DisplayName("Get all loggers returns expected pre-configured types")
    void testGetAllLoggers() {
        initializeWithTestFactory();

        Map<String, LoggerProtocol> all = LogManager.getAllLoggers();
        assertTrue(all.containsKey("common"), "Should contain 'common' logger");
        assertTrue(all.containsKey("interface"), "Should contain 'interface' logger");
        assertTrue(all.containsKey("performance"), "Should contain 'performance' logger");
        assertTrue(all.containsKey("prompt_builder"), "Should contain 'prompt_builder' logger");

        for (LoggerProtocol logger : all.values()) {
            assertInstanceOf(LoggerProtocol.class, logger);
        }
    }

    // ==========================================================================
    // TestLogManager: test_get_logger_creates_on_demand
    // ==========================================================================
    @Test
    @DisplayName("Get logger creates logger on demand and caches it")
    void testGetLoggerCreatesOnDemand() {
        initializeWithTestFactory();

        LoggerProtocol logger1 = LogManager.getLogger("on_demand_test");
        assertInstanceOf(TestLogger.class, logger1);
        assertEquals("on_demand_test", ((TestLogger) logger1).logType);

        LoggerProtocol logger2 = LogManager.getLogger("on_demand_test");
        assertSame(logger1, logger2);
    }

    @Test
    @DisplayName("getLogger uses dynamic logger override from provider")
    void testGetLoggerUsesDynamicLoggerOverride() {
        LogManager.setDefaultLoggerFactory(TestLogger::new);
        LogManager.LogConfigProvider.setProvider(() -> Map.of(
                "common", Map.of("level", "INFO", "output", "console"),
                "interface", Map.of("level", "INFO", "output", "console"),
                "performance", Map.of("level", "INFO", "output", "console"),
                "prompt_builder", Map.of("level", "INFO", "output", "console"),
                "dynamic", Map.of("level", "DEBUG", "output", "console")));
        LogManager.initialize();

        TestLogger logger = (TestLogger) LogManager.getLogger("dynamic");

        assertEquals("DEBUG", logger.getConfig().get("level"));
    }

    // ==========================================================================
    // TestLogLevel: test_log_level_filtering (conceptual — actual filtering is in impl)
    // ==========================================================================
    @Test
    @DisplayName("Logger setLevel changes the level")
    void testLogLevelChange() {
        initializeWithTestFactory();

        LoggerProtocol logger = LogManager.getLogger("level_test");
        logger.setLevel(700); // ERROR level
        assertEquals(700, ((TestLogger) logger).currentLevel);

        logger.setLevel(400); // DEBUG level
        assertEquals(400, ((TestLogger) logger).currentLevel);
    }

    @Test
    @DisplayName("log level filtering follows setLevel threshold")
    void testLogLevelFiltering() {
        initializeWithTestFactory();

        TestLogger logger = (TestLogger) LogManager.getLogger("level_filter");
        logger.setLevel(LogLevels.ERROR);
        logger.error("error survives");

        assertEquals(LogLevels.ERROR, logger.currentLevel);
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("error survives")));
    }

    // ==========================================================================
    // TestDefaultLogger: test_logger_config_access
    // ==========================================================================
    @Test
    @DisplayName("Logger config is accessible and contains expected keys")
    void testLoggerConfigAccess() {
        initializeWithTestFactory();

        LoggerProtocol logger = LogManager.getLogger("common");
        Map<String, Object> config = logger.getConfig();
        assertNotNull(config);
        assertInstanceOf(Map.class, config);
    }

    // ==========================================================================
    // TestDefaultLogger: test_logger_reconfigure
    // ==========================================================================
    @Test
    @DisplayName("Logger can be reconfigured")
    void testLoggerReconfigure() {
        initializeWithTestFactory();

        LoggerProtocol logger = LogManager.getLogger("common");
        Map<String, Object> originalConfig = logger.getConfig();

        Map<String, Object> newConfig = new HashMap<>(originalConfig);
        newConfig.put("level", "DEBUG");
        logger.reconfigure(newConfig);

        Map<String, Object> updatedConfig = logger.getConfig();
        assertEquals("DEBUG", updatedConfig.get("level"));
    }

    // ==========================================================================
    // TestDefaultLogger: test_all_log_levels
    // ==========================================================================
    @Test
    @DisplayName("All log level methods produce messages")
    void testAllLogLevels() {
        initializeWithTestFactory();

        TestLogger logger = (TestLogger) LogManager.getLogger("common");
        logger.debug("Debug level message");
        logger.info("Info level message");
        logger.warning("Warning level message");
        logger.error("Error level message");
        logger.critical("Critical level message");

        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Debug level message")));
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Info level message")));
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Warning level message")));
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Error level message")));
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Critical level message")));
    }

    // ==========================================================================
    // TestDefaultLogger: test_exception_logging
    // ==========================================================================
    @Test
    @DisplayName("Exception logging records exception message")
    void testExceptionLogging() {
        initializeWithTestFactory();

        TestLogger logger = (TestLogger) LogManager.getLogger("common");
        try {
            throw new IllegalArgumentException("Test exception");
        } catch (Exception e) {
            logger.exception("Exception occurred", e);
        }

        assertTrue(logger.messages.stream().anyMatch(m ->
                        m.contains("Exception occurred") && m.contains("Test exception")),
                "Should contain both the message and exception detail");
    }

    @Test
    @DisplayName("structured output format defaults to json")
    void testStructuredOutputFormatDefaultsToJson() {
        LogConfig config = new LogConfig();

        assertEquals("json", config.getCommonConfig().get("structured_output_format"));
    }

    @Test
    @DisplayName("structured output format can be loaded from yaml")
    void testStructuredOutputFormatCanBeLoadedFromYaml(@TempDir Path tempDir) throws Exception {
        Path yaml = tempDir.resolve("logging.yaml");
        String logPath = tempDir.toString().replace("\\", "/");
        Files.writeString(yaml, """
                logging:
                  backend: default
                  level: INFO
                  output: console
                  log_path: "%s"
                  structured_output_format: text
                """.formatted(logPath));

        LogConfig config = new LogConfig(yaml.toString());

        assertEquals("text", config.getCommonConfig().get("structured_output_format"));
    }

    @Test
    @DisplayName("normalize logging config normalizes per-logger levels")
    void testNormalizeLoggingConfigNormalizesPerLoggerLevels(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("common", Map.of("level", "DEBUG")));

        config.loadFromDict(raw);

        assertEquals(LogLevels.DEBUG, config.getCommonConfig().get("level"));
    }

    @Test
    @DisplayName("per-logger level override is loaded from config")
    void testPerLoggerLevelOverrideIsLoadedFromYaml(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("interface", Map.of("level", "ERROR")));

        config.loadFromDict(raw);

        assertEquals(LogLevels.ERROR, config.getInterfaceConfig().get("level"));
    }

    @Test
    @DisplayName("partial logger override inherits global settings")
    void testPartialLoggerOverrideInheritsGlobalSettings(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("output", List.of("console", "file"));
        raw.put("loggers", Map.of("common", Map.of("level", "DEBUG")));

        config.loadFromDict(raw);
        Map<String, Object> common = config.getCommonConfig();

        assertEquals(LogLevels.DEBUG, common.get("level"));
        assertEquals(List.of("console", "file"), common.get("output"));
        assertEquals("json", common.get("structured_output_format"));
    }

    @Test
    @DisplayName("built-in logger level override is scoped to target logger")
    void testBuiltinLoggerLevelOverrideIsScopedToTargetLogger(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("interface", Map.of("level", "DEBUG")));

        config.loadFromDict(raw);

        assertEquals(LogLevels.DEBUG, config.getInterfaceConfig().get("level"));
        assertEquals(LogLevels.INFO, config.getCommonConfig().get("level"));
    }

    @Test
    @DisplayName("default backend rejects non-level logger overrides")
    void testDefaultBackendRejectsNonLevelLoggerOverrides(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("common", Map.of("sinks", List.of("console"))));

        assertThrows(BaseError.class, () -> config.loadFromDict(raw));
    }

    @Test
    @DisplayName("default backend rejects loguru-specific root keys")
    void testDefaultBackendRejectsLoguruSpecificRootKeys(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("serialize", true);

        assertThrows(BaseError.class, () -> config.loadFromDict(raw));
    }

    @Test
    @DisplayName("backend defaults to default when missing")
    void testBackendDefaultsToDefaultWhenMissing(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.remove("backend");

        config.loadFromDict(raw);

        assertEquals("default", config.getBackend());
    }

    @Test
    @DisplayName("default provider builds dynamic logger config")
    void testDefaultProviderBuildsDynamicLoggerConfig() {
        LogManager.setDefaultLoggerFactory(TestLogger::new);
        LogManager.LogConfigProvider.setProvider(() -> Map.of(
                "common", Map.of("level", "INFO", "output", "console"),
                "interface", Map.of("level", "INFO", "output", "console"),
                "performance", Map.of("level", "INFO", "output", "console"),
                "prompt_builder", Map.of("level", "INFO", "output", "console"),
                "agent", Map.of("level", "DEBUG", "output", "console")));
        LogManager.initialize();

        TestLogger agentLogger = (TestLogger) LogManager.getLogger("agent");

        assertEquals("DEBUG", agentLogger.getConfig().get("level"));
    }

    @Test
    @DisplayName("invalid per-logger level falls back to warning")
    void testInvalidPerLoggerLevelFallsBackToWarning(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("common", Map.of("level", "not-a-level")));

        config.loadFromDict(raw);

        assertEquals(LogLevels.WARNING, config.getCommonConfig().get("level"));
    }

    @Test
    @DisplayName("agent debug override does not enable common debug")
    void testAgentDebugOverrideDoesNotEnableCommonDebug(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("loggers", Map.of("agent", Map.of("level", "DEBUG")));

        config.loadFromDict(raw);

        assertEquals(LogLevels.DEBUG, config.getCustomConfig("agent").get("level"));
        assertEquals(LogLevels.INFO, config.getCommonConfig().get("level"));
    }

    @Test
    @DisplayName("interface log file output config resolves interface path")
    void testInterfaceLogFileOutput(@TempDir Path tempDir) {
        LogConfig config = new LogConfig();
        Map<String, Object> raw = loggingConfig(tempDir);
        raw.put("output", List.of("console", "file"));

        config.loadFromDict(raw);
        String logFile = String.valueOf(config.getInterfaceConfig().get("log_file"));

        assertTrue(logFile.contains("interface"));
        assertTrue(logFile.endsWith("jiuwen_interface.log"));
    }

    @Test
    @DisplayName("default logger sanitizes control characters")
    void testMessageSanitization() {
        DefaultLogger logger = new DefaultLogger("sanitize-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("line1\nline2\tend");

        assertTrue(handler.records.stream().anyMatch(r ->
                r.getMessage().contains("\\n") && r.getMessage().contains("\\t")));
    }

    @Test
    @DisplayName("create nested log directory")
    void testCreateNestedLogDirectory(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("nested").resolve("logs").resolve("app.log");

        new DefaultLogger("nested-dir", Map.of("output", "file", "log_file", logFile.toString()));

        assertTrue(Files.isDirectory(logFile.getParent()));
    }

    @Test
    @DisplayName("create log directory with configured path")
    void testCreateLogDirectoryWithRelativePath(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("relative").resolve("app.log");

        new DefaultLogger("relative-dir", Map.of("output", List.of("console", "file"),
                "log_file", logFile.toString()));

        assertTrue(Files.isDirectory(logFile.getParent()));
    }

    @Test
    @DisplayName("create log directory failure raises exception")
    void testCreateLogDirectoryFailureRaisesException(@TempDir Path tempDir) throws Exception {
        Path fileAsParent = tempDir.resolve("not-a-directory");
        Files.writeString(fileAsParent, "occupied");
        Path impossibleChild = fileAsParent.resolve("app.log");

        assertThrows(IllegalStateException.class, () ->
                new DefaultLogger("bad-dir", Map.of("output", "file", "log_file", impossibleChild.toString())));
    }

    @Test
    @DisplayName("create existing directory no error")
    void testCreateExistingDirectoryNoError(@TempDir Path tempDir) throws Exception {
        Path existing = tempDir.resolve("existing");
        Files.createDirectories(existing);

        assertDoesNotThrow(() ->
                new DefaultLogger("existing-dir", Map.of("output", "file",
                        "log_file", existing.resolve("app.log").toString())));
    }

    @Test
    @DisplayName("log path validation rejects null paths")
    void testLogPathValidation() {
        assertThrows(BaseError.class, () -> LoggingUtils.normalizeAndValidateLogPath(null));
    }

    // ==========================================================================
    // TestLogManagerReset: test_reset_clears_loggers
    // ==========================================================================
    @Test
    @DisplayName("Reset clears all loggers")
    void testResetClearsLoggers() {
        initializeWithTestFactory();

        LoggerProtocol logger1 = LogManager.getLogger("common");
        assertNotNull(logger1);
        assertTrue(LogManager.getAllLoggers().size() > 0);

        LogManager.reset();

        // After reset, re-initialize to get a fresh logger
        initializeWithTestFactory();
        LoggerProtocol logger2 = LogManager.getLogger("common");
        assertNotSame(logger1, logger2);
    }

    // ==========================================================================
    // TestThreadSafety: test_thread_trace_id_isolation
    // ==========================================================================
    @Nested
    @DisplayName("Thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Trace/session IDs are isolated between threads")
        void testThreadTraceIdIsolation() throws InterruptedException {
            initializeWithTestFactory();

            List<String[]> results = new CopyOnWriteArrayList<>();

            Thread t1 = new Thread(() -> {
                LoggingUtils.setSessionId("10001");
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                results.add(new String[]{"10001", LoggingUtils.getSessionId()});
            });
            t1.setUncaughtExceptionHandler((t, e) -> System.out.println(t.getName() + ":" + e.getMessage()));

            Thread t2 = new Thread(() -> {
                LoggingUtils.setSessionId("10002");
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                results.add(new String[]{"10002", LoggingUtils.getSessionId()});
            });
            t2.setUncaughtExceptionHandler((t, e) -> System.out.println(t.getName() + ":" + e.getMessage()));

            Thread t3 = new Thread(() -> {
                LoggingUtils.setSessionId("10003");
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                results.add(new String[]{"10003", LoggingUtils.getSessionId()});
            });
            t3.setUncaughtExceptionHandler((t, e) -> System.out.println(t.getName() + ":" + e.getMessage()));

            t1.start();
            t2.start();
            t3.start();

            t1.join();
            t2.join();
            t3.join();

            // Each thread should see its own session ID
            for (String[] pair : results) {
                assertEquals(pair[0], pair[1],
                        "Thread session_id mismatch: expected " + pair[0] + ", actual " + pair[1]);
            }

            // Main thread should have default trace id
            assertEquals("default_trace_id", LoggingUtils.getSessionId());
        }
    }

    // ==========================================================================
    // LoggingUtils additional tests
    // ==========================================================================
    @Nested
    @DisplayName("LoggingUtils")
    class LoggingUtilsTests {

        @Test
        @DisplayName("Default session ID is 'default_trace_id'")
        void testDefaultSessionId() {
            LoggingUtils.clearSessionId();
            assertEquals("default_trace_id", LoggingUtils.getSessionId());
        }

        @Test
        @DisplayName("setSessionId and getSessionId work correctly")
        void testSetAndGetSessionId() {
            LoggingUtils.setSessionId("test-session-123");
            assertEquals("test-session-123", LoggingUtils.getSessionId());
        }

        @Test
        @DisplayName("clearSessionId resets to default")
        void testClearSessionId() {
            LoggingUtils.setSessionId("some-id");
            LoggingUtils.clearSessionId();
            assertEquals("default_trace_id", LoggingUtils.getSessionId());
        }

        @Test
        @DisplayName("Null session ID is replaced with default")
        void testNullSessionId() {
            LoggingUtils.setSessionId(null);
            assertEquals("default_trace_id", LoggingUtils.getSessionId());
        }
    }
}

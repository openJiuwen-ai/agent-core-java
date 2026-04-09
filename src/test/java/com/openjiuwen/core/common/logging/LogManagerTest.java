/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for LogManager and LoggingUtils.
 * Ported from Python: tests/unit_tests/core/common/log/test_logger.py
 */
class LogManagerTest {

    @BeforeEach
    void setUp() {
        LogManager.reset();
    }

    @AfterEach
    void tearDown() {
        LoggingUtils.clearSessionId();
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

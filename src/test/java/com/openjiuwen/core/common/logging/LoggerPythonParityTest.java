/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.logging.defaults.DefaultConfigProvider;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.defaults.LogConfig;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.common.log.test_logger} in
 * {@code tests/unit_tests/core/common/log/test_logger.py}.</p>
 */
class LoggerPythonParityTest {

    @TestFactory
    List<DynamicTest> loggerPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestThreadSafety::test_thread_trace_id_isolation", this::threadTraceIdIsolation);
        add(tests, "TestLogManager::test_custom_logger_registration_and_usage",
                this::customLoggerRegistrationAndUsage);
        add(tests, "TestLogManager::test_default_logger_creation", this::defaultLoggerCreation);
        add(tests, "TestLogManager::test_get_all_loggers", this::getAllLoggers);
        add(tests, "TestLogManager::test_register_logger_type_check", this::registerLoggerTypeCheck);
        add(tests, "TestLogManager::test_get_logger_creates_on_demand", this::getLoggerCreatesOnDemand);
        add(tests, "TestLogManager::test_get_logger_uses_dynamic_logger_override",
                this::getLoggerUsesDynamicLoggerOverride);
        add(tests, "TestLogConfig::test_structured_output_format_defaults_to_json",
                this::structuredOutputFormatDefaultsToJson);
        add(tests, "TestLogConfig::test_structured_output_format_can_be_loaded_from_yaml",
                this::structuredOutputFormatCanBeLoadedFromYaml);
        add(tests, "TestLogConfig::test_normalize_logging_config_normalizes_per_logger_levels",
                this::normalizeLoggingConfigNormalizesPerLoggerLevels);
        add(tests, "TestLogConfig::test_per_logger_level_override_is_loaded_from_yaml",
                this::perLoggerLevelOverrideIsLoadedFromYaml);
        add(tests, "TestLogConfig::test_partial_logger_override_inherits_global_settings",
                this::partialLoggerOverrideInheritsGlobalSettings);
        add(tests, "TestLogConfig::test_builtin_logger_level_override_is_scoped_to_target_logger",
                this::builtinLoggerLevelOverrideIsScopedToTargetLogger);
        add(tests, "TestLogConfig::test_default_backend_rejects_non_level_logger_overrides",
                this::defaultBackendRejectsNonLevelLoggerOverrides);
        add(tests, "TestLogConfig::test_default_backend_rejects_loguru_specific_root_keys",
                this::defaultBackendRejectsLoguruSpecificRootKeys);
        add(tests, "TestLogConfig::test_backend_defaults_to_default_when_missing",
                this::backendDefaultsToDefaultWhenMissing);
        add(tests, "TestLogConfig::test_default_provider_builds_dynamic_logger_config",
                this::defaultProviderBuildsDynamicLoggerConfig);
        add(tests, "TestLogConfig::test_invalid_per_logger_level_falls_back_to_warning",
                this::invalidPerLoggerLevelFallsBackToWarning);
        add(tests, "TestPerLoggerBehavior::test_agent_debug_override_does_not_enable_common_debug",
                this::agentDebugOverrideDoesNotEnableCommonDebug);
        add(tests, "TestLogLevel::test_log_level_filtering", this::logLevelFiltering);
        add(tests, "TestLogFileOutput::test_interface_log_file_output", this::interfaceLogFileOutput);
        add(tests, "TestDefaultLogger::test_message_sanitization", this::messageSanitization);
        add(tests, "TestDefaultLogger::test_logger_config_access", this::loggerConfigAccess);
        add(tests, "TestDefaultLogger::test_logger_reconfigure", this::loggerReconfigure);
        add(tests, "TestDefaultLogger::test_all_log_levels", this::allLogLevels);
        add(tests, "TestDefaultLogger::test_exception_logging", this::exceptionLogging);
        add(tests, "TestLogManagerReset::test_reset_clears_loggers", this::resetClearsLoggers);
        add(tests, "TestLogDirectoryCreation::test_create_nested_log_directory",
                this::createNestedLogDirectory);
        add(tests, "TestLogDirectoryCreation::test_create_log_directory_with_relative_path",
                this::createLogDirectoryWithRelativePath);
        add(tests, "TestLogDirectoryCreation::test_create_log_directory_failure_raises_exception",
                this::createLogDirectoryFailureRaisesException);
        add(tests, "TestLogDirectoryCreation::test_create_existing_directory_no_error",
                this::createExistingDirectoryNoError);
        add(tests, "TestLogDirectoryCreation::test_log_path_validation", this::logPathValidation);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, ThrowingRunnable runnable) {
        Executable executable = () -> runIsolated(runnable);
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private static void runIsolated(ThrowingRunnable runnable) throws Exception {
        LogManager.reset();
        LogManager.LogConfigProvider.setProvider(null);
        LoggingDefaults.reset();
        LoggingUtils.setSessionId();
        try {
            runnable.run();
        } finally {
            LogManager.reset();
            LogManager.LogConfigProvider.setProvider(null);
            LoggingDefaults.reset();
            LoggingUtils.setSessionId();
        }
    }

    private void threadTraceIdIsolation() throws Exception {
        RecordingLogger commonLogger = new RecordingLogger("common");
        LogManager.LogConfigProvider.setProvider(() -> Map.of("common", config(LogLevels.INFO)));
        LogManager.setDefaultLoggerFactory((logType, config) -> commonLogger);

        List<List<String>> recorded = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        List<Thread> threads = List.of(
                threadForSession("10001", recorded, latch),
                threadForSession("10002", recorded, latch),
                threadForSession("10003", recorded, latch)
        );
        for (Thread thread : threads) {
            thread.start();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        for (Thread thread : threads) {
            thread.join();
        }

        for (List<String> pair : recorded) {
            assertEquals(pair.get(0), pair.get(1));
        }
        assertEquals("default_trace_id", LoggingUtils.getSessionId());
        assertTrue(commonLogger.infoMessages.toString().contains("10001"));
        assertTrue(commonLogger.infoMessages.toString().contains("10002"));
        assertTrue(commonLogger.infoMessages.toString().contains("10003"));
    }

    private static Thread threadForSession(String sessionId, List<List<String>> recorded, CountDownLatch latch) {
        return new Thread(() -> {
            LoggerProtocol logger = LogManager.getLogger("common");
            LoggingUtils.setSessionId(sessionId);
            logger.info("Thread started with session id " + sessionId);
            recorded.add(List.of(sessionId, LoggingUtils.getSessionId()));
            latch.countDown();
        });
    }

    private void customLoggerRegistrationAndUsage() {
        CustomLogger customLogger = new CustomLogger();
        LogManager.registerLogger("custom", customLogger);

        LoggerProtocol retrieved = LogManager.getLogger("custom");
        assertSame(customLogger, retrieved);
        retrieved.info("Test custom logger");

        assertEquals(List.of("CUSTOM LOGGER INFO: Test custom logger"), customLogger.infoMessages);
    }

    private void defaultLoggerCreation() {
        LoggingDefaults.configureLogConfig(props(
                "level", "INFO",
                "output", List.of("console"),
                "log_path", tempDirectory().toString()
        ));

        LoggerProtocol newLogger = LogManager.getLogger("new_type_test");
        DefaultLogger defaultLogger = assertInstanceOf(DefaultLogger.class, newLogger);
        CapturingHandler handler = new CapturingHandler();
        defaultLogger.addHandler(handler);
        defaultLogger.setLevel(LogLevels.INFO);

        defaultLogger.warning("Test new logger type");

        assertTrue(handler.messages.toString().contains("Test new logger type"));
        assertTrue(defaultLogger.getConfig().get("log_file").toString().endsWith("new_type_test.log"));
    }

    private void getAllLoggers() {
        LogManager.LogConfigProvider.setProvider(() -> Map.of(
                "common", config(LogLevels.INFO),
                "interface", config(LogLevels.INFO),
                "prompt_builder", config(LogLevels.INFO),
                "performance", config(LogLevels.INFO)
        ));

        Map<String, LoggerProtocol> loggers = LogManager.getAllLoggers();

        assertTrue(loggers.keySet().containsAll(List.of("common", "interface", "prompt_builder", "performance")));
        loggers.values().forEach(logger -> assertInstanceOf(LoggerProtocol.class, logger));
    }

    private void registerLoggerTypeCheck() {
        assertThrows(LogManager.TypeError.class, () -> LogManager.registerLogger("invalid", null));
    }

    private void getLoggerCreatesOnDemand() {
        LoggingDefaults.configureLogConfig(props(
                "level", "INFO",
                "output", List.of("console"),
                "log_path", tempDirectory().toString()
        ));

        LoggerProtocol newTypeLogger = LogManager.getLogger("on_demand_test");
        assertInstanceOf(DefaultLogger.class, newTypeLogger);
        assertTrue(newTypeLogger.getConfig().get("log_file").toString().endsWith("on_demand_test.log"));

        LoggerProtocol sameLogger = LogManager.getLogger("on_demand_test");
        assertSame(newTypeLogger, sameLogger);
    }

    private void getLoggerUsesDynamicLoggerOverride() {
        Path tempDir = tempDirectory();
        LoggingDefaults.configureLogConfig(props(
                "level", "INFO",
                "output", List.of("console", "file"),
                "log_path", tempDir.toString(),
                "loggers", Map.of("foo", Map.of("level", "DEBUG"))
        ));

        LoggerProtocol logger = LogManager.getLogger("foo");

        assertEquals(LogLevels.DEBUG, logger.getConfig().get("level"));
        assertEquals(List.of("console", "file"), logger.getConfig().get("output"));
        assertEquals(tempDir.resolve("foo.log").normalize().toString(), logger.getConfig().get("log_file"));
    }

    private void structuredOutputFormatDefaultsToJson() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  log_path: "%s"
                  log_file: common.log
                  output:
                    - console
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals("json", config.getCommonConfig().get("structured_output_format"));
    }

    private void structuredOutputFormatCanBeLoadedFromYaml() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  structured_output_format: text
                  log_path: "%s"
                  log_file: common.log
                  output:
                    - console
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals("text", config.getCommonConfig().get("structured_output_format"));
    }

    @SuppressWarnings("unchecked")
    private void normalizeLoggingConfigNormalizesPerLoggerLevels() {
        Map<String, Object> normalized = LogLevels.normalizeLoggingConfig(props(
                "level", "INFO",
                "loggers", Map.of(
                        "agent", Map.of("level", "DEBUG"),
                        "invalid_logger", Map.of("level", "NOT_A_LEVEL")
                )
        ));

        assertEquals(LogLevels.INFO, normalized.get("level"));
        Map<String, Object> loggers = (Map<String, Object>) normalized.get("loggers");
        assertEquals(LogLevels.DEBUG, ((Map<?, ?>) loggers.get("agent")).get("level"));
        assertEquals(LogLevels.WARNING, ((Map<?, ?>) loggers.get("invalid_logger")).get("level"));
    }

    private void perLoggerLevelOverrideIsLoadedFromYaml() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  output: [console, file]
                  log_path: "%s"
                  loggers:
                    agent:
                      level: DEBUG
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals(LogLevels.INFO, config.getCommonConfig().get("level"));
        assertEquals(LogLevels.DEBUG, config.getCustomConfig("agent").get("level"));
        assertEquals(LogLevels.INFO, config.getCustomConfig("other").get("level"));
    }

    private void partialLoggerOverrideInheritsGlobalSettings() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  output:
                    - console
                  format: "%%(levelname)s | %%(message)s"
                  log_path: "%s"
                  loggers:
                    agent:
                      level: DEBUG
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());
        Map<String, Object> commonConfig = config.getCommonConfig();
        Map<String, Object> agentConfig = config.getCustomConfig("agent");

        assertEquals(LogLevels.DEBUG, agentConfig.get("level"));
        assertEquals(commonConfig.get("output"), agentConfig.get("output"));
        assertEquals(commonConfig.get("format"), agentConfig.get("format"));
        assertEquals(tempDir.resolve("agent.log").normalize().toString(), agentConfig.get("log_file"));
    }

    private void builtinLoggerLevelOverrideIsScopedToTargetLogger() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  output: [console, file]
                  log_path: "%s"
                  log_file: common.log
                  interface_log_file: interface.log
                  interface_output: [console, file]
                  loggers:
                    interface:
                      level: DEBUG
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals(LogLevels.INFO, config.getCommonConfig().get("level"));
        assertEquals(LogLevels.DEBUG, config.getInterfaceConfig().get("level"));
    }

    private void defaultBackendRejectsNonLevelLoggerOverrides() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  output: [console, file]
                  structured_output_format: json
                  log_path: "%s"
                  loggers:
                    foo:
                      level: DEBUG
                      output: [console]
                """.formatted(escapeYamlPath(tempDir)));

        assertThrows(RuntimeException.class, () -> new LogConfig(configPath.toString()));
    }

    private void defaultBackendRejectsLoguruSpecificRootKeys() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  backend: default
                  level: INFO
                  output: [console]
                  log_path: "%s"
                  sinks:
                    console:
                      target: stdout
                """.formatted(escapeYamlPath(tempDir)));

        assertThrows(RuntimeException.class, () -> new LogConfig(configPath.toString()));
    }

    private void backendDefaultsToDefaultWhenMissing() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  output: [console]
                  log_path: "%s"
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals("default", config.getBackend());
        assertEquals("default", config.getCommonConfig().get("backend"));
    }

    private void defaultProviderBuildsDynamicLoggerConfig() {
        Path tempDir = tempDirectory();
        Map<String, Object> normalized = DefaultConfigProvider.loadDefaultBackendConfig(props(
                "level", "INFO",
                "output", List.of("console"),
                "log_path", tempDir.toString(),
                "loggers", Map.of("agent", Map.of("level", "DEBUG"))
        ));

        Map<String, Object> agentConfig = DefaultConfigProvider.buildDefaultLoggerConfig(normalized, "agent");

        assertEquals("default", agentConfig.get("backend"));
        assertEquals(LogLevels.DEBUG, agentConfig.get("level"));
        assertEquals(List.of("console"), agentConfig.get("output"));
        assertEquals(tempDir.resolve("agent.log").normalize().toString(), agentConfig.get("log_file"));
    }

    private void invalidPerLoggerLevelFallsBackToWarning() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYaml(tempDir, """
                logging:
                  level: INFO
                  log_path: "%s"
                  loggers:
                    agent:
                      level: NOT_A_LEVEL
                """.formatted(escapeYamlPath(tempDir)));

        LogConfig config = new LogConfig(configPath.toString());

        assertEquals(LogLevels.WARNING, config.getCustomConfig("agent").get("level"));
    }

    private void agentDebugOverrideDoesNotEnableCommonDebug() {
        Path tempDir = tempDirectory();
        LoggingDefaults.configureLogConfig(props(
                "level", "INFO",
                "output", List.of("console"),
                "structured_output_format", "text",
                "log_path", tempDir.toString(),
                "log_file", "common.log",
                "interface_log_file", "interface.log",
                "prompt_builder_interface_log_file", "prompt_builder.log",
                "performance_log_file", "performance.log",
                "interface_output", List.of("console"),
                "performance_output", List.of("console"),
                "loggers", Map.of("agent", Map.of("level", "DEBUG"))
        ));

        RecordingLogger commonLogger = new RecordingLogger("common");
        RecordingLogger agentLogger = new RecordingLogger("agent");
        LogManager.setDefaultLoggerFactory((logType, config) -> {
            RecordingLogger logger = "agent".equals(logType) ? agentLogger : commonLogger;
            logger.reconfigure(config);
            return logger;
        });

        LoggerProtocol common = LogManager.getLogger("common");
        LoggerProtocol agent = LogManager.getLogger("agent");
        common.debug("common debug should stay hidden");
        common.info("common info should be visible");
        agent.debug("agent debug should be visible");

        assertFalse(commonLogger.debugMessages.contains("common debug should stay hidden"));
        assertTrue(commonLogger.infoMessages.contains("common info should be visible"));
        assertTrue(agentLogger.debugMessages.contains("agent debug should be visible"));
    }

    private void logLevelFiltering() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler("level_test", handler);

        logger.setLevel(LogLevels.DEBUG);
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warning("Warning message");
        logger.error("Error message");

        assertTrue(handler.messages.toString().contains("Debug message"));
        assertTrue(handler.messages.toString().contains("Info message"));
        assertTrue(handler.messages.toString().contains("Warning message"));
        assertTrue(handler.messages.toString().contains("Error message"));

        handler.clear();
        logger.setLevel(LogLevels.ERROR);
        logger.debug("Should not appear debug");
        logger.info("Should not appear info");
        logger.warning("Should not appear warning");
        logger.error("Should appear error");

        assertFalse(handler.messages.toString().contains("Should not appear debug"));
        assertFalse(handler.messages.toString().contains("Should not appear info"));
        assertFalse(handler.messages.toString().contains("Should not appear warning"));
        assertTrue(handler.messages.toString().contains("Should appear error"));
    }

    private void interfaceLogFileOutput() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler("interface", handler);
        LoggingUtils.setSessionId("FILE-TEST-123");

        logger.info("This is a test message for file output");

        assertTrue(handler.lastMessage().contains("This is a test message for file output"));
        assertEquals("FILE-TEST-123", LoggingUtils.getSessionId());
    }

    private void messageSanitization() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler("common", handler);

        logger.info("Test message\nwith newline\r\nand carriage return\r");

        String output = handler.lastMessage();
        assertTrue(output.contains("Test message"));
        assertFalse(output.contains("\r"));
        assertFalse(output.contains("\n"));
        assertTrue(output.contains("with newline"));
        assertTrue(output.contains("and carriage return"));
    }

    private void loggerConfigAccess() {
        DefaultLogger logger = loggerWithHandler("common", new CapturingHandler());

        Map<String, Object> config = logger.getConfig();

        assertNotNull(config);
        assertTrue(config.containsKey("log_file"));
        assertTrue(config.containsKey("output"));
        assertTrue(config.containsKey("level"));
    }

    private void loggerReconfigure() {
        DefaultLogger logger = loggerWithHandler("common", new CapturingHandler());
        Map<String, Object> newConfig = new LinkedHashMap<>(logger.getConfig());
        newConfig.put("level", LogLevels.DEBUG);

        logger.reconfigure(newConfig);

        assertEquals(LogLevels.DEBUG, logger.getConfig().get("level"));
    }

    private void allLogLevels() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler("common", handler);
        logger.setLevel(LogLevels.DEBUG);

        logger.debug("Debug level message");
        logger.info("Info level message");
        logger.warning("Warning level message");
        logger.error("Error level message");
        logger.critical("Critical level message");

        assertTrue(handler.messages.toString().contains("Debug level message"));
        assertTrue(handler.messages.toString().contains("Info level message"));
        assertTrue(handler.messages.toString().contains("Warning level message"));
        assertTrue(handler.messages.toString().contains("Error level message"));
        assertTrue(handler.messages.toString().contains("Critical level message"));
    }

    private void exceptionLogging() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler("common", handler);
        logger.setLevel(LogLevels.ERROR);

        logger.exception("Exception occurred", new IllegalArgumentException("Test exception"));

        assertTrue(handler.lastMessage().contains("Exception occurred"));
        assertTrue(handler.lastThrown().toString().contains("Test exception"));
    }

    private void resetClearsLoggers() {
        LoggingDefaults.configureLogConfig(props(
                "level", "INFO",
                "output", List.of("console"),
                "log_path", tempDirectory().toString()
        ));
        LogManager.getLogger("common");
        LogManager.getLogger("interface");
        assertFalse(LogManager.getAllLoggers().isEmpty());

        LogManager.reset();

        assertInstanceOf(DefaultLogger.class, LogManager.getLogger("common"));
    }

    private void createNestedLogDirectory() throws IOException {
        Path tempDir = tempDirectory();
        Path nestedLogFile = tempDir.resolve("logs").resolve("run").resolve("test.log");

        DefaultLogger logger = new DefaultLogger("test_nested", props(
                "log_file", nestedLogFile.toString(),
                "output", List.of("file"),
                "level", LogLevels.INFO,
                "format", "%(asctime)s | %(levelname)s | %(message)s"
        ));

        assertTrue(Files.isDirectory(nestedLogFile.getParent()));
        logger.info("Test nested directory log");
    }

    private void createLogDirectoryWithRelativePath() throws IOException {
        Path tempDir = tempDirectory();
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            Path relativeLogFile = Path.of("logs", "run", "relative_test.log");
            Path absoluteLogFile = tempDir.resolve(relativeLogFile).normalize();

            DefaultLogger logger = new DefaultLogger("test_relative", props(
                    "log_file", absoluteLogFile.toString(),
                    "output", List.of("file"),
                    "level", LogLevels.INFO,
                    "format", "%(asctime)s | %(levelname)s | %(message)s"
            ));

            assertTrue(Files.isDirectory(absoluteLogFile.getParent()));
            logger.info("Test relative path log");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private void createLogDirectoryFailureRaisesException() throws IOException {
        Path tempDir = tempDirectory();
        Path parentAsFile = Files.createFile(tempDir.resolve("not-a-directory"));
        Path invalidNestedFile = parentAsFile.resolve("test.log");

        assertThrows(IllegalStateException.class, () -> new DefaultLogger("test_failure", props(
                "log_file", invalidNestedFile.toString(),
                "output", List.of("file"),
                "level", LogLevels.INFO,
                "format", "%(asctime)s | %(levelname)s | %(message)s"
        )));
    }

    private void createExistingDirectoryNoError() throws IOException {
        Path tempDir = tempDirectory();
        Path existingLogPath = Files.createDirectories(tempDir.resolve("logs").resolve("existing"));
        Path existingLogFile = existingLogPath.resolve("test.log");

        DefaultLogger logger = assertDoesNotThrow(() -> new DefaultLogger("test_existing", props(
                "log_file", existingLogFile.toString(),
                "output", List.of("file"),
                "level", LogLevels.INFO,
                "format", "%(asctime)s | %(levelname)s | %(message)s"
        )));

        assertTrue(Files.exists(existingLogPath));
        logger.info("Test existing directory");
    }

    private void logPathValidation() {
        String sensitivePath = System.getProperty("os.name").toLowerCase().contains("win")
                ? "C:\\Windows\\System32\\test.log"
                : "/etc/passwd";

        assertThrows(RuntimeException.class, () -> LoggingUtils.normalizeAndValidateLogPath(sensitivePath));
    }

    private static DefaultLogger loggerWithHandler(String logType, CapturingHandler handler) {
        DefaultLogger logger = new DefaultLogger(logType, props(
                "output", List.of("console"),
                "log_file", tempDirectory().resolve(logType + ".log").toString(),
                "level", LogLevels.DEBUG
        ));
        logger.setLevel(LogLevels.DEBUG);
        logger.addHandler(handler);
        return logger;
    }

    private static Map<String, Object> config(int level) {
        return props("output", List.of("console"), "level", level, "log_file", tempDirectory().resolve("test.log"));
    }

    private static Map<String, Object> props(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("logger-python-parity-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory", e);
        }
    }

    private static Path writeYaml(Path tempDir, String content) throws IOException {
        Path configPath = tempDir.resolve("test_config.yaml");
        Files.writeString(configPath, content);
        return configPath;
    }

    private static String escapeYamlPath(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    @SuppressWarnings("unused")
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class RecordingLogger implements LoggerProtocol {
        private final String name;
        private final List<String> debugMessages = new CopyOnWriteArrayList<>();
        protected final List<String> infoMessages = new CopyOnWriteArrayList<>();
        private final List<String> warningMessages = new CopyOnWriteArrayList<>();
        private final List<String> errorMessages = new CopyOnWriteArrayList<>();
        private final Map<String, Object> config = new LinkedHashMap<>();

        RecordingLogger(String name) {
            this.name = name;
        }

        @Override
        public void debug(String msg, Object... args) {
            if (LogLevels.normalizeLogLevel(config.getOrDefault("level", LogLevels.DEBUG)) <= LogLevels.DEBUG) {
                debugMessages.add(msg);
            }
        }

        @Override
        public void info(String msg, Object... args) {
            if (LogLevels.normalizeLogLevel(config.getOrDefault("level", LogLevels.INFO)) <= LogLevels.INFO) {
                infoMessages.add(msg);
            }
        }

        @Override
        public void warning(String msg, Object... args) {
            warningMessages.add(msg);
        }

        @Override
        public void error(String msg, Object... args) {
            errorMessages.add(msg);
        }

        @Override
        public void critical(String msg, Object... args) {
            errorMessages.add(msg);
        }

        @Override
        public void exception(String msg, Throwable t, Object... args) {
            errorMessages.add(msg + ": " + t);
        }

        @Override
        public void log(int level, String msg, Object... args) {
            if (level >= LogLevels.ERROR) {
                error(msg, args);
            } else if (level >= LogLevels.WARNING) {
                warning(msg, args);
            } else if (level >= LogLevels.INFO) {
                info(msg, args);
            } else {
                debug(msg, args);
            }
        }

        @Override
        public void setLevel(int level) {
            config.put("level", level);
        }

        @Override
        public Map<String, Object> getConfig() {
            return new LinkedHashMap<>(config);
        }

        @Override
        public void reconfigure(Map<String, Object> newConfig) {
            config.clear();
            if (newConfig != null) {
                config.putAll(newConfig);
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class CustomLogger extends RecordingLogger {
        private CustomLogger() {
            super("custom");
        }

        @Override
        public void info(String msg, Object... args) {
            infoMessages.add("CUSTOM LOGGER INFO: " + msg);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        private String lastMessage() {
            assertFalse(messages.isEmpty(), "expected at least one captured log record");
            return messages.getLast();
        }

        private Throwable lastThrown() {
            assertFalse(records.isEmpty(), "expected at least one captured log record");
            return records.getLast().getThrown();
        }

        private void clear() {
            records.clear();
            messages.clear();
        }

        @Override
        public void setLevel(Level newLevel) {
            super.setLevel(newLevel);
        }
    }

    private record ValueRef<T>(AtomicReference<T> ref) {
    }
}

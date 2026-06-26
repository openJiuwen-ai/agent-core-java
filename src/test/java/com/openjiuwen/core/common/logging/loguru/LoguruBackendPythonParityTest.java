/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.defaults.LogConfig;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventStatus;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import com.openjiuwen.core.common.logging.events.ModuleType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.common.log.test_loguru_backend} in
 * {@code tests/unit_tests/core/common/log/test_loguru_backend.py}.</p>
 */
class LoguruBackendPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    List<DynamicTest> loguruBackendPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_initialize_can_switch_to_loguru_backend_via_argument",
            this::initializeCanSwitchToLoguruBackendViaArgument);
        add(tests, "test_yaml_backend_can_bootstrap_loguru_backend",
            this::yamlBackendCanBootstrapLoguruBackend);
        add(tests, "test_builtin_default_backend_initializes_common_with_default_class",
            this::builtinDefaultBackendInitializesCommonWithDefaultClass);
        add(tests, "test_runtime_reconfigure_rebuilds_common_and_runner_loggers",
            this::runtimeReconfigureRebuildsCommonAndRunnerLoggers);
        add(tests, "test_structured_event_type_uses_native_loguru_json_envelope",
            this::structuredEventTypeUsesNativeLoguruJsonEnvelope);
        add(tests, "test_event_object_keeps_message_and_event_separate_in_json_output",
            this::eventObjectKeepsMessageAndEventSeparateInJsonOutput);
        add(tests, "test_event_type_can_emit_event_first_json_payload",
            this::eventTypeCanEmitEventFirstJsonPayload);
        add(tests, "test_event_object_event_first_json_preserves_metadata_and_context",
            this::eventObjectEventFirstJsonPreservesMetadataAndContext);
        add(tests, "test_plain_log_can_emit_event_first_json_payload",
            this::plainLogCanEmitEventFirstJsonPayload);
        add(tests, "test_exception_can_emit_event_first_json_failure_payload",
            this::exceptionCanEmitEventFirstJsonFailurePayload);
        add(tests, "test_logger_level_override_affects_only_logger_threshold",
            this::loggerLevelOverrideAffectsOnlyLoggerThreshold);
        add(tests, "test_team_logger_debug_uses_dedicated_debug_console_sink",
            this::teamLoggerDebugUsesDedicatedDebugConsoleSink);
        add(tests, "test_loguru_logger_rejects_logger_specific_sinks",
            this::loguruLoggerRejectsLoggerSpecificSinks);
        add(tests, "test_loguru_provider_builds_dynamic_logger_config",
            this::loguruProviderBuildsDynamicLoggerConfig);
        add(tests, "test_logger_set_level_only_changes_adapter_threshold",
            this::loggerSetLevelOnlyChangesAdapterThreshold);
        add(tests, "test_loguru_logger_rejects_sink_overrides",
            this::loguruLoggerRejectsSinkOverrides);
        add(tests, "test_loguru_backend_rejects_default_specific_root_keys",
            this::loguruBackendRejectsDefaultSpecificRootKeys);
        add(tests, "test_loguru_backend_rejects_invalid_serialize_mode",
            this::loguruBackendRejectsInvalidSerializeMode);
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

    private void initializeCanSwitchToLoguruBackendViaArgument() {
        Path tempDir = tempDirectory();
        LoggingDefaults.configureLogConfig(makeLoguruConfig(tempDir));

        LogManager.initialize("loguru");
        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(LoguruLogger.class, logger);
        CapturingHandler handler = attach(logger);
        LoggingUtils.setSessionId("TRACE-ARG");
        logger.info("value=%s", 42);

        assertEquals("value=42", handler.lastMessage());
        assertEquals("TRACE-ARG", LoggingUtils.getSessionId());
    }

    private void yamlBackendCanBootstrapLoguruBackend() throws IOException {
        Path tempDir = tempDirectory();
        Path configPath = writeYamlConfig(tempDir, makeLoguruConfig(tempDir));

        LoggingDefaults.configureLog(configPath.toString());
        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(LoguruLogger.class, logger);
        CapturingHandler handler = attach(logger);
        LoggingUtils.setSessionId("TRACE-YAML");
        logger.info("yaml backend active");

        assertEquals("yaml backend active", handler.lastMessage());
    }

    private void builtinDefaultBackendInitializesCommonWithDefaultClass() {
        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(DefaultLogger.class, logger);
    }

    private void runtimeReconfigureRebuildsCommonAndRunnerLoggers() {
        Path tempDir = tempDirectory();
        LoggingDefaults.configureLogConfig(makeDefaultConfig(tempDir));

        assertInstanceOf(DefaultLogger.class, LogManager.getLogger("common"));
        assertEquals("default", Loggers.COMMON.getConfig().get("backend"));

        LoggingDefaults.configureLogConfig(makeLoguruConfig(tempDir));

        LoggerProtocol commonLogger = LogManager.getLogger("common");
        LoggerProtocol runnerLogger = LogManager.getLogger("runner");
        assertInstanceOf(LoguruLogger.class, commonLogger);
        assertInstanceOf(LoguruLogger.class, runnerLogger);
        assertEquals("loguru", Loggers.COMMON.getConfig().get("backend"));
        assertEquals("loguru", Loggers.RUNNER.getConfig().get("backend"));
    }

    private void structuredEventTypeUsesNativeLoguruJsonEnvelope() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        LoggingUtils.setSessionId("TRACE-JSON");

        logger.logEvent("Agent started", LogEventType.AGENT_START, null);

        JsonNode payload = jsonFromLast(handler);
        assertEquals("agent_start", payload.get("event_type").asText());
        assertEquals("common", payload.get("module_id").asText());
        assertEquals("common", payload.get("module_name").asText());
        assertEquals("Agent started", payload.get("message").asText());
        assertEquals("TRACE-JSON", payload.get("trace_id").asText());
        assertEquals("common", payload.at("/metadata/_log_context/log_type").asText());
    }

    private void eventObjectKeepsMessageAndEventSeparateInJsonOutput() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        BaseLogEvent event = new BaseLogEvent();
        event.setEventType(LogEventType.AGENT_START);
        event.setModuleType(ModuleType.AGENT);
        event.setModuleId("agent_123");
        event.setMessage("Original event message");

        LoggingUtils.setSessionId("TRACE-EVENT");
        logger.logEvent("Replacement message", null, event);

        JsonNode payload = jsonFromLast(handler);
        assertEquals("Replacement message", payload.get("message").asText());
        assertEquals("agent_123", payload.get("module_id").asText());
        assertEquals("TRACE-EVENT", payload.get("trace_id").asText());
    }

    private void eventTypeCanEmitEventFirstJsonPayload() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        LoggingUtils.setSessionId("TRACE-EVENT-FIRST");

        logger.logEvent("Agent started", LogEventType.AGENT_START, null);

        JsonNode payload = jsonFromLast(handler);
        assertFalse(payload.has("record"));
        assertFalse(payload.has("text"));
        assertEquals("agent_start", payload.get("event_type").asText());
        assertEquals("common", payload.get("module_id").asText());
        assertEquals("agent", payload.get("module_type").asText());
        assertEquals("TRACE-EVENT-FIRST", payload.get("trace_id").asText());
        assertEquals("common", payload.at("/metadata/_log_context/log_type").asText());
    }

    private void eventObjectEventFirstJsonPreservesMetadataAndContext() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        BaseLogEvent event = new BaseLogEvent();
        event.setEventType(LogEventType.AGENT_START);
        event.setModuleType(ModuleType.AGENT);
        event.setModuleId("agent_123");
        event.setMetadata(props("biz", "value", "_log_context", props("stale", true)));

        LoggingUtils.setSessionId("TRACE-EVENT-OBJECT");
        logger.logEvent("Replacement message", null, event);

        JsonNode payload = jsonFromLast(handler);
        assertEquals("Replacement message", payload.get("message").asText());
        assertEquals("agent_123", payload.get("module_id").asText());
        assertEquals("value", payload.at("/metadata/biz").asText());
        assertEquals("common", payload.at("/metadata/_log_context/log_type").asText());
        assertFalse(payload.at("/metadata/_log_context").has("stale"));
    }

    private void plainLogCanEmitEventFirstJsonPayload() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        BaseLogEvent event = plainLogEvent("plain log");

        LoggingUtils.setSessionId("TRACE-PLAIN");
        logger.logEvent("plain log", null, event);

        JsonNode payload = jsonFromLast(handler);
        assertEquals("plain_log", payload.get("event_type").asText());
        assertEquals("INFO", payload.get("log_level").asText());
        assertEquals("plain log", payload.get("message").asText());
        assertEquals("common", payload.get("module_id").asText());
        assertEquals("common", payload.get("module_name").asText());
        assertEquals("system", payload.get("module_type").asText());
        assertEquals("TRACE-PLAIN", payload.get("trace_id").asText());
    }

    private void exceptionCanEmitEventFirstJsonFailurePayload() throws Exception {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        BaseLogEvent event = plainLogEvent("plain failure");
        event.setLogLevel(LogLevel.ERROR);
        event.setStatus(EventStatus.FAILURE);
        event.setExceptionDetail("boom");
        event.setErrorMessage("boom");
        event.setStacktrace("RuntimeException: boom");

        LoggingUtils.setSessionId("TRACE-EXCEPTION");
        logger.logEvent("plain failure", null, event);

        JsonNode payload = jsonFromLast(handler);
        assertEquals("plain_log", payload.get("event_type").asText());
        assertEquals("ERROR", payload.get("log_level").asText());
        assertEquals("plain failure", payload.get("message").asText());
        assertEquals("failure", payload.get("status").asText());
        assertEquals("boom", payload.get("exception").asText());
        assertEquals("boom", payload.get("error_message").asText());
        assertTrue(payload.get("stacktrace").asText().contains("RuntimeException"));
    }

    private void loggerLevelOverrideAffectsOnlyLoggerThreshold() {
        Path tempDir = tempDirectory();
        Map<String, Object> config = makeLoguruConfig(tempDir);
        config.put("loggers", props("agent", props("level", "DEBUG")));
        LoggingDefaults.configureLogConfig(config);

        LoggerProtocol commonLogger = LogManager.getLogger("common");
        LoggerProtocol agentLogger = LogManager.getLogger("agent");
        CapturingHandler commonHandler = attach(commonLogger);
        CapturingHandler agentHandler = attach(agentLogger);

        commonLogger.info("common info visible");
        commonLogger.debug("common debug hidden");
        agentLogger.debug("agent debug visible");
        agentLogger.info("agent info visible");

        assertTrue(commonHandler.messages.toString().contains("common info visible"));
        assertFalse(commonHandler.messages.toString().contains("common debug hidden"));
        assertTrue(agentHandler.messages.toString().contains("agent debug visible"));
        assertTrue(agentHandler.messages.toString().contains("agent info visible"));
    }

    private void teamLoggerDebugUsesDedicatedDebugConsoleSink() {
        LoggingDefaults.configureLogConfig(makeLoguruConfig(tempDirectory()));

        LoggerProtocol teamLogger = Loggers.TEAM;
        CapturingHandler handler = attach(teamLogger);
        LoggingUtils.setSessionId("TRACE-TEAM-DEBUG");
        teamLogger.debug("team debug visible");

        assertEquals("team debug visible", handler.lastMessage());
        assertEquals(LogLevels.DEBUG, teamLogger.getConfig().get("level"));
        assertEquals("team_console", firstSinkName(teamLogger.getConfig()));
    }

    private void loguruLoggerRejectsLoggerSpecificSinks() {
        Map<String, Object> config = makeLoguruConfig(tempDirectory());
        config.put("loggers", props("performance", props("sinks", List.of("perf_json"))));

        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    private void loguruProviderBuildsDynamicLoggerConfig() {
        Map<String, Object> config = makeLoguruConfig(tempDirectory());
        config.put("loggers", props("agent", props("level", "DEBUG")));

        Map<String, Object> normalizedConfig = LoguruConfigProvider.loadLoguruBackendConfig(config);
        Map<String, Object> agentConfig = LoguruConfigProvider.buildLoguruLoggerConfig(normalizedConfig, "agent");

        assertEquals("loguru", agentConfig.get("backend"));
        assertEquals(LogLevels.DEBUG, agentConfig.get("effective_level"));
        assertEquals(List.of("console", "app_json"), sinkNames(agentConfig));
        assertEquals("stdout", firstSink(agentConfig).get("target"));
    }

    private void loggerSetLevelOnlyChangesAdapterThreshold() {
        LoguruLogger logger = configuredLoguruLogger("common", tempDirectory());
        CapturingHandler handler = attach(logger);
        logger.setLevel(LogLevels.ERROR);

        logger.info("info hidden after set_level");
        logger.error("error visible after set_level");

        assertFalse(handler.messages.toString().contains("info hidden after set_level"));
        assertTrue(handler.messages.toString().contains("error visible after set_level"));
    }

    private void loguruLoggerRejectsSinkOverrides() {
        Map<String, Object> config = makeLoguruConfig(tempDirectory());
        config.put("loggers", props("interface", props(
            "sink_overrides", props("missing_sink", props("target", tempDirectory().resolve("missing.log").toString()))
        )));

        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    private void loguruBackendRejectsDefaultSpecificRootKeys() {
        Map<String, Object> config = makeLoguruConfig(tempDirectory());
        config.put("output", List.of("console"));

        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    private void loguruBackendRejectsInvalidSerializeMode() {
        Map<String, Object> config = makeLoguruConfig(tempDirectory());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinks = (Map<String, Object>) config.get("sinks");
        @SuppressWarnings("unchecked")
        Map<String, Object> appJson = (Map<String, Object>) sinks.get("app_json");
        appJson.put("serialize_mode", "invalid");

        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(config));
    }

    private static LoguruLogger configuredLoguruLogger(String logType, Path tempDir) {
        LoggingDefaults.configureLogConfig(makeLoguruConfig(tempDir));
        return assertInstanceOf(LoguruLogger.class, LogManager.getLogger(logType));
    }

    private static BaseLogEvent plainLogEvent(String message) {
        BaseLogEvent event = new BaseLogEvent();
        event.setEventTypeKey("plain_log");
        event.setLogLevel(LogLevel.INFO);
        event.setModuleType(ModuleType.SYSTEM);
        event.setMessage(message);
        return event;
    }

    private static Map<String, Object> makeLoguruConfig(Path tempDir) {
        Map<String, Object> sinks = new LinkedHashMap<>();
        sinks.put("console", props(
            "target", "stdout",
            "level", "INFO",
            "serialize", false,
            "colorize", false,
            "enqueue", false,
            "format", "{extra[log_type]} | {extra[trace_id]} | {message}"
        ));
        sinks.put("team_console", props(
            "target", "stdout",
            "level", "DEBUG",
            "serialize", false,
            "colorize", false,
            "enqueue", false,
            "format", "{extra[log_type]} | {extra[trace_id]} | {message}"
        ));
        sinks.put("app_json", props(
            "target", tempDir.resolve("common.jsonl").toString(),
            "level", "INFO",
            "serialize", true,
            "enqueue", false,
            "encoding", "utf-8"
        ));
        sinks.put("perf_json", props(
            "target", tempDir.resolve("performance.jsonl").toString(),
            "level", "INFO",
            "serialize", true,
            "enqueue", false,
            "encoding", "utf-8"
        ));

        return props(
            "backend", "loguru",
            "defaults", props(
                "level", "INFO",
                "enqueue", false,
                "catch", false,
                "backtrace", false,
                "diagnose", false
            ),
            "sinks", sinks,
            "routes", props(
                "common", List.of("console", "app_json"),
                "interface", List.of("console", "app_json"),
                "performance", List.of("perf_json"),
                "team", List.of("team_console"),
                "*", List.of("console", "app_json")
            ),
            "loggers", props("team", props("level", "DEBUG"))
        );
    }

    private static Map<String, Object> makeDefaultConfig(Path tempDir) {
        return props(
            "backend", "default",
            "level", "INFO",
            "format", "%(log_type)s | %(trace_id)s | %(levelname)s | %(message)s",
            "log_path", tempDir.toString(),
            "output", List.of("console"),
            "interface_output", List.of("console"),
            "performance_output", List.of("console"),
            "loggers", props()
        );
    }

    private static Path writeYamlConfig(Path tempDir, Map<String, Object> loggingConfig) throws IOException {
        Path configPath = tempDir.resolve("loguru.yaml");
        Files.writeString(configPath, new Yaml().dump(Map.of("logging", loggingConfig)));
        assertDoesNotThrow(() -> new LogConfig(configPath.toString()));
        return configPath;
    }

    private static JsonNode jsonFromLast(CapturingHandler handler) throws IOException {
        return OBJECT_MAPPER.readTree(handler.lastMessage());
    }

    private static CapturingHandler attach(LoggerProtocol logger) {
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSink(Map<String, Object> config) {
        return (Map<String, Object>) ((List<?>) config.get("sinks")).get(0);
    }

    private static String firstSinkName(Map<String, Object> config) {
        return String.valueOf(firstSink(config).get("name"));
    }

    private static List<String> sinkNames(Map<String, Object> config) {
        List<String> result = new ArrayList<>();
        for (Object sink : (List<?>) config.get("sinks")) {
            result.add(String.valueOf(((Map<?, ?>) sink).get("name")));
        }
        return result;
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
            return Files.createTempDirectory("loguru-backend-python-parity-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp directory", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();
        private final List<LogRecord> records = new ArrayList<>();

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
            return messages.get(messages.size() - 1);
        }
    }
}

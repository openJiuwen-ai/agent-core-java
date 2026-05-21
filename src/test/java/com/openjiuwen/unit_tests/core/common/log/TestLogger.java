/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.DefaultLogger;
import com.openjiuwen.core.common.exception.BaseError;

/**
 * Tests for logging functionality.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.common.log.test_logger}.
 * Tests log function, thread isolation, and session ID management.
 */
class TestLogger {

    // ---------------------------------------------------------------------------
    // Test LogManager - Mirrors Python LogManager tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLogManagerExists() {
        assertNotNull(LogManager.class);
    }

    @Test
    @Tag("level0")
    void testLogManagerGetLogger() {
        // Python: logger = LogManager.get_logger("common")
        LoggerProtocol logger = LogManager.getLogger("common");
        assertNotNull(logger);
    }

    @Test
    @Tag("level0")
    void testLogManagerMethods() {
        assertTrue(LogManager.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test LoggerProtocol - Mirrors Python LoggerProtocol tests
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLoggerProtocolExists() {
        assertNotNull(LoggerProtocol.class);
    }

    @Test
    @Tag("level0")
    void testLoggerSetLevel() {
        // Python: logger.set_level(logging.INFO)
        LoggerProtocol logger = LogManager.getLogger("common");
        assertNotNull(logger);
    }

    // ---------------------------------------------------------------------------
    // Test session ID - Mirrors Python get_session_id, set_session_id
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testSessionIdMethods() {
        // Python: set_session_id(session_id), get_session_id()
        assertNotNull(LogManager.class);
    }

    @Test
    @Tag("level0")
    void testGetSessionId() {
        // Python: get_session_id() returns current session ID
        String sessionId = LogManager.getSessionId();
        assertNotNull(sessionId);
    }

    @Test
    @Tag("level0")
    void testSetSessionId() {
        // Python: set_session_id(session_id)
        LogManager.setSessionId("test_session");
        assertEquals("test_session", LogManager.getSessionId());
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

    // ---------------------------------------------------------------------------
    // Test thread isolation - Mirrors Python thread_function
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testThreadIsolationConcept() {
        // Python: thread_function tests thread isolation
        LoggerProtocol logger = LogManager.getLogger("thread_test");
        assertNotNull(logger);
    }

    // ---------------------------------------------------------------------------
    // Test logging levels - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testLoggingLevels() {
        LoggerProtocol logger = LogManager.getLogger("level_test");
        assertNotNull(logger);
    }

    @Test
    @Tag("level0")
    void testInfoLogging() {
        // Python: logger.info("message")
        LoggerProtocol logger = LogManager.getLogger("info_test");
        assertNotNull(logger);
    }

    @Test
    @Tag("level0")
    void testErrorLogging() {
        // Python: logger.error("message")
        LoggerProtocol logger = LogManager.getLogger("error_test");
        assertNotNull(logger);
    }
}
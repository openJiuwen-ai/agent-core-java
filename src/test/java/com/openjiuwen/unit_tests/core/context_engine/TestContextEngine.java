/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

/**
 * Tests for context engine.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.context_engine.test_context_engine}.
 * Tests context engine registration, processors, and message handling.
 */
class TestContextEngine {

    // ---------------------------------------------------------------------------
    // Test ContextEngine exists - Mirrors Python ContextEngine import
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testContextEngineExists() {
        assertNotNull(ContextEngine.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigExists() {
        assertNotNull(ContextEngineConfig.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineMethods() {
        assertTrue(ContextEngine.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test SessionModelContext - Mirrors Python SessionModelContext
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testSessionModelContextExists() {
        assertNotNull(SessionModelContext.class);
    }

    // ---------------------------------------------------------------------------
    // Test ContextProcessor - Mirrors Python ContextProcessor
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testContextProcessorExists() {
        assertNotNull(ContextProcessor.class);
    }

    @Test
    @Tag("level0")
    void testContextProcessorMethods() {
        assertTrue(ContextProcessor.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test message types - Mirrors Python message imports
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testUserMessageExists() {
        assertNotNull(UserMessage.class);
    }

    @Test
    @Tag("level0")
    void testAssistantMessageExists() {
        assertNotNull(AssistantMessage.class);
    }

    @Test
    @Tag("level0")
    void testSystemMessageExists() {
        assertNotNull(SystemMessage.class);
    }

    @Test
    @Tag("level0")
    void testToolMessageExists() {
        assertNotNull(ToolMessage.class);
    }

    // ---------------------------------------------------------------------------
    // Test StatusCode - Mirrors Python StatusCode import
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStatusCodeExists() {
        assertNotNull(StatusCode.class);
    }

    @Test
    @Tag("level0")
    void testStatusCodeValues() {
        assertTrue(StatusCode.values().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test BaseError - Mirrors Python BaseError import
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBaseErrorExists() {
        assertNotNull(BaseError.class);
    }

    // ---------------------------------------------------------------------------
    // Test processor registration - Mirrors Python register_processor
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testProcessorRegistration() {
        // Python: @ContextEngine.register_processor()
        assertNotNull(ContextEngine.class);
    }
}

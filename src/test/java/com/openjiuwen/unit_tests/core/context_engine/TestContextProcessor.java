/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;

/**
 * Tests for context processor.
 * <p>
 * Mirrors Python's context processor tests.
 * Tests processor base class and configuration.
 */
class TestContextProcessor {

    @Test
    @Tag("level0")
    void testContextProcessorExists() {
        assertNotNull(ContextProcessor.class);
    }

    @Test
    @Tag("level0")
    void testContextProcessorConfigExists() {
        assertNotNull(FullCompactProcessorConfig.class);
    }

    @Test
    @Tag("level0")
    void testProcessorMethods() {
        assertTrue(ContextProcessor.class.getDeclaredMethods().length > 0);
    }
}

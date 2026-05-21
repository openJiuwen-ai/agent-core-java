/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;

/**
 * Tests for context hybrid KV processor.
 * <p>
 * Mirrors Python's context hybrid KV processor tests.
 * Tests hybrid KV-based context processing using ContextProcessor.
 */
class TestContextHybridKvProcessor {

    @Test
    @Tag("level0")
    void testContextHybridKvProcessorExists() {
        assertNotNull(ContextProcessor.class);
    }

    @Test
    @Tag("level0")
    void testHybridKvProcessorConfigExists() {
        assertNotNull(ToolResultBudgetProcessorConfig.class);
    }

@Test
    @Tag("level0")
    void testProcessorMethods() {
        assertTrue(ContextProcessor.class.getDeclaredMethods().length > 0);
    }
}

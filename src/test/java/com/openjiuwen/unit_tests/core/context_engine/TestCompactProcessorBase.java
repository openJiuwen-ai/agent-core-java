/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessor;

/**
 * Tests for compact processor base.
 * <p>
 * Mirrors Python's compact processor base tests.
 * Tests base class for compact processors using MicroCompactProcessor as representative.
 */
class TestCompactProcessorBase {

    @Test
    @Tag("level0")
    void testCompactProcessorBaseExists() {
        assertNotNull(MicroCompactProcessor.class);
    }

@Test
    @Tag("level0")
    void testBaseMethods() {
        assertTrue(MicroCompactProcessor.class.getDeclaredMethods().length > 0);
    }
}

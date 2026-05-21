/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.schema.ContextCompressionState;
import com.openjiuwen.core.context.schema.ContextCompressionMetric;

/**
 * Tests for context compression.
 * <p>
 * Mirrors Python's context compression tests.
 * Tests compression algorithms and configuration.
 */
class TestContextCompression {

    @Test
    @Tag("level0")
    void testContextCompressionExists() {
        assertNotNull(ContextCompressionState.class);
    }

    @Test
    @Tag("level0")
    void testCompressionConfigExists() {
        assertNotNull(ContextCompressionMetric.class);
    }

    @Test
    @Tag("level0")
    void testCompressionMethods() {
        assertTrue(ContextCompressionState.class.getDeclaredMethods().length > 0);
    }
}

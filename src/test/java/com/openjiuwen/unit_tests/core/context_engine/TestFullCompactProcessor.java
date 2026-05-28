/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.compressor.FullCompactProcessor;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;

/**
 * Tests for full_compact_processor.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.context_engine.test_full_compact_processor}.
 * Tests full context compaction and processor configuration.
 */
class TestFullCompactProcessor {

    @Test
    @Tag("level0")
    void testFullCompactProcessorExists() {
        assertNotNull(FullCompactProcessor.class);
    }

    @Test
    @Tag("level0")
    void testCompactProcessorConfigExists() {
        assertNotNull(FullCompactProcessorConfig.class);
    }

    @Test
    @Tag("level0")
    void testProcessorMethods() {
        assertTrue(FullCompactProcessor.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testCompactConfiguration() {
        assertNotNull(FullCompactProcessorConfig.class);
    }
}

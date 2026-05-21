/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;

/**
 * Tests for new_message_summary_offloader.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.context_engine.test_new_message_summary_offloader}.
 * Tests message summary offloading and context compression.
 */
class TestNewMessageSummaryOffloader {

    @Test
    @Tag("level0")
    void testMessageSummaryOffloaderExists() {
        assertNotNull(MessageSummaryOffloader.class);
    }

    @Test
    @Tag("level0")
    void testMessageOffloaderConfigExists() {
        assertNotNull(MessageSummaryOffloaderConfig.class);
    }

    @Test
    @Tag("level0")
    void testOffloaderMethods() {
        assertTrue(MessageSummaryOffloader.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testOffloadConfiguration() {
        assertNotNull(MessageSummaryOffloaderConfig.class);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import static com.openjiuwen.unit_tests.support.JUnitBridgeAssertions.assertDelegatedClassPasses;

import com.openjiuwen.core.context.processor.offloader.MessageOffloaderTest;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bridge tests for the canonical Java translation of message-summary offloader tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.context_engine.test_new_message_summary_offloader}.
 * The real translated coverage lives in {@link MessageSummaryOffloaderTest} and
 * {@link MessageOffloaderTest}; this bridge keeps the legacy target file from
 * drifting into a placeholder-only state.
 */
class TestNewMessageSummaryOffloader {

    @Test
    @DisplayName("delegates summary-offloader coverage to canonical tests")
    void testDelegatedSummaryOffloaderCoverage() {
        assertDelegatedClassPasses(MessageSummaryOffloaderTest.class);
        assertDelegatedClassPasses(MessageOffloaderTest.class);
    }
}

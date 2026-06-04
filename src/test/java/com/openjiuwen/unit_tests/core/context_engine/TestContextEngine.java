/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import static com.openjiuwen.unit_tests.support.JUnitBridgeAssertions.assertDelegatedClassPasses;

import com.openjiuwen.core.context.ContextEngineTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bridge tests for the canonical Java translation of Python's context-engine tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.context_engine.test_context_engine}.
 * The real translated test coverage lives in {@link ContextEngineTest}; this bridge
 * keeps the legacy target file aligned instead of leaving a stale placeholder.
 */
class TestContextEngine {
    @Test
    @DisplayName("delegates to canonical ContextEngineTest")
    void testDelegatedContextEngineCoverage() {
        assertDelegatedClassPasses(ContextEngineTest.class);
    }
}

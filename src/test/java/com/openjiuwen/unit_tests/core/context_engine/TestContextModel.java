/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import static com.openjiuwen.unit_tests.support.JUnitBridgeAssertions.assertDelegatedClassPasses;

import com.openjiuwen.core.context.ModelContextTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bridge tests for the canonical Java translation of Python's model-context tests.
 * <p>
 * Mirrors Python's {@code test_context_model.py}.
 * <p>
 * The real translated test coverage lives in {@link ModelContextTest}; this bridge
 * refreshes the legacy target file so it no longer stays as a disabled placeholder.
 */
class TestContextModel {
    @Test
    @DisplayName("delegates to canonical ModelContextTest")
    void testDelegatedModelContextCoverage() {
        assertDelegatedClassPasses(ModelContextTest.class);
    }
}

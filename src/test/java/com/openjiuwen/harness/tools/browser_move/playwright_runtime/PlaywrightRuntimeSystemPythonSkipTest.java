/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_playwright_runtime_end_to_end} in
 * {@code tests/system_tests/harness/tools/browser_move/test_playwright_runtime.py}.</p>
 */
class PlaywrightRuntimeSystemPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: Set RUN_BROWSER_MOVE_SYSTEM_TESTS=1 to run browser_move system tests.";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testPlaywrightRuntimeEndToEnd() {
    }
}

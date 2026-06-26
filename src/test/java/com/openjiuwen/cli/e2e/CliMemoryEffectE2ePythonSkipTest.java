/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests/cli/e2e/test_memory_effect.py}.</p>
 */
class CliMemoryEffectE2ePythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: E2E test requires real LLM API credentials";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testMemoryAffectsBehavior() {
    }
}

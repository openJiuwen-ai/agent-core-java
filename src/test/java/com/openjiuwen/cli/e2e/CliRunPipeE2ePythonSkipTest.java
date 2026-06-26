/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests/cli/e2e/test_run_pipe.py}.</p>
 */
class CliRunPipeE2ePythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: E2E test requires real LLM API credentials";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void runPipeMode() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void runAutoStdinDetection() {
    }
}

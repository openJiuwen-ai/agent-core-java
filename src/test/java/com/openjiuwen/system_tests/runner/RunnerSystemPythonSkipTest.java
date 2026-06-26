/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.runner;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestRunner} in
 * {@code tests/system_tests/runner/test_runner.py}.</p>
 */
class RunnerSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: skip system test";

    private static final String PYTHON_NETWORK_SKIP_REASON =
            "Skipped in Python source: skip system test - requires network";

    @Disabled(PYTHON_NETWORK_SKIP_REASON)
    @Test
    void testConnectAndListToolsWithQueryAk() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testMcpToolsPlaywright() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testMcpToolsSse() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testMcpToolsStdio() {
    }

    @Disabled(PYTHON_NETWORK_SKIP_REASON)
    @Test
    void testRunnerAgentResourceManagement() {
    }

    @Disabled(PYTHON_NETWORK_SKIP_REASON)
    @Test
    void testWorkflowAgentInvokeWithInterruptRecovery() {
    }
}

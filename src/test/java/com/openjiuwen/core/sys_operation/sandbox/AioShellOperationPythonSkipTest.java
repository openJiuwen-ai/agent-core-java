/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's AIO sandbox shell-operation tests in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/test_aio_shell_operation.py}.</p>
 */
class AioShellOperationPythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: Requires running AIO sandbox on "
            + "http://localhost:8080; start the service or set RUN_REAL_AIO_SANDBOX_TESTS=1 to force-enable.";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellBasicExecution() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellEnvironmentVariables() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellCwd() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellTimeout() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellPingTimeout() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void shellListTools() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void executeCmdStreamBasic() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void executeCmdStreamTimeout() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void executeCmdStreamEmptyCommand() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void executeCmdStreamContinuousOutput() {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's AIO sandbox real integration tests in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/test_aio_sandbox_real.py}.</p>
 */
class AioSandboxRealPythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: Requires running AIO sandbox; "
            + "the real AIO sandbox fixture requires http://localhost:8080 or RUN_REAL_AIO_SANDBOX_TESTS=1.";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioShellExecuteCmd() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioShellExecuteCmdStream() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioFsWriteAndReadFile() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioFsListFiles() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioFsSearchFiles() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void realAioCodeExecute() {
    }
}

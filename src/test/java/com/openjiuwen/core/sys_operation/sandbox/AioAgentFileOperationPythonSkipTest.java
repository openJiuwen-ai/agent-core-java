/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestAIOAgentFileOperation} in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/test_aio_agent.py}.</p>
 */
class AioAgentFileOperationPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: Requires running AIO sandbox and LLM configuration";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testAgentWriteAndReadFile() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testAgentListAndSearchFiles() {
    }
}

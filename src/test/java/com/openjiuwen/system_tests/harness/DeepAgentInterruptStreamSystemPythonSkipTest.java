/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestDeepAgentInterruptStream} in
 * {@code tests/system_tests/harness/test_deepagent_interrupt_stream.py}.</p>
 */
class DeepAgentInterruptStreamSystemPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testDeepagentStreamInterruptResume() {
    }
}

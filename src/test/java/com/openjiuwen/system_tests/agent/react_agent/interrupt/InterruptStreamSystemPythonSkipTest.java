/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_interrupt_stream} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_interrupt_stream.py}.</p>
 */
class InterruptStreamSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailStreamInterruptDetected() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailStreamAgreeWithAutoconfirm() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailStreamReject() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailStreamConcurrentToolsAllConfirmed() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailStreamConcurrentToolsPartialReject() {
    }
}

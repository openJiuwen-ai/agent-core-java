/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests/system_tests/agent/react_agent/interrupt/test_interrupt_exception_scenarios.py}.</p>
 */
class InterruptExceptionScenariosSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void recoveryWithWrongToolCallId() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void emptyInteractiveInputRecovery() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionSwitchRecovery() {
    }
}

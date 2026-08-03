/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_react_agent_interrupt_concurrent_tools} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_react_agent_interrupt_concurrent_tools.py}.</p>
 */
class ReActAgentInterruptConcurrentToolsSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailConcurrentToolsAllConfirmed() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailConcurrentToolsPartialRejectOneRound() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailConcurrentToolsPartialRejectTwoRounds() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailConcurrentToolsOnePassOneInterrupt() {
    }
}

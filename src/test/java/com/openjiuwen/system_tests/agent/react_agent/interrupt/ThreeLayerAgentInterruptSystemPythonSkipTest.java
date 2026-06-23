/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_3layer_agent_interrupt} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_3layer_agent_interrupt.py}.</p>
 */
class ThreeLayerAgentInterruptSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentInterrupt() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentParallelInterrupt() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentAutoConfirmClearSession() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentSubagentParallelInterrupt() {
    }
}

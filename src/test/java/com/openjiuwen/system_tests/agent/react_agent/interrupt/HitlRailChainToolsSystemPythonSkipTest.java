/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_hitl_rail_chain_tools} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_hitl_rail_chain_tools.py}.</p>
 */
class HitlRailChainToolsSystemPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testHitlRailChainTools() {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_react_agent_auto_confirm} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_react_agent_auto_confirm.py}.</p>
 */
class ReActAgentAutoConfirmSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailAutoConfirm() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailSameToolMultipleCalls() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailConfirmOneAutoPassOthers() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void hitlRailClearSessionAfterReject() {
    }
}

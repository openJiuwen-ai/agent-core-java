/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_fine_grained_auto_confirm} in
 * {@code tests/system_tests/agent/react_agent/interrupt/test_fine_grained_auto_confirm.py}.</p>
 */
class FineGrainedAutoConfirmSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void singleAgentFineGrainedAutoConfirm() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentFineGrainedAutoConfirm() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void threeLayerAgentFineGrainedAutoConfirmClearSession() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void fineGrainedAutoConfirmMergeKeys() {
    }
}

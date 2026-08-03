/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code RealWorkflowTest} in
 * {@code tests/system_tests/workflow/test_real_workflow.py}.</p>
 */
class RealWorkflowSystemMissingTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: skip system test";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testStreamWorkflowLlmWithStreamWriter() {
        // Python decorates this real workflow system scenario with unittest.skip.
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testWorkflowLlmQuestionerPlugin() {
        // Python decorates this real workflow system scenario with unittest.skip.
    }
}

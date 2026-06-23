/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.workflow_agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code MultiWorkflowAgentTest} in
 * {@code tests/system_tests/agent/workflow_agent/test_multi_workflow_agent.py}.</p>
 */
class MultiWorkflowAgentSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: skip system test";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void defaultResponseStreamReturnsWorkflowFinal() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void defaultResponseWhenNoTaskDetected() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void endBatchOutputShouldHaveWorkflowFinal() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void endStreamOutputShouldHaveEndNodeStream() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void fallbackToFirstWorkflowWhenNoDefaultResponse() {
    }
}

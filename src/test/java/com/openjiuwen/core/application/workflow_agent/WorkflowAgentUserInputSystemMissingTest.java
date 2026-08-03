/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code WorkflowAgentUserInputTest} in
 * {@code tests/system_tests/agent/workflow_agent/test_workflow_agent_user_input.py}.</p>
 */
class WorkflowAgentUserInputSystemMissingTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: skip system test";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testDictInterruptShouldReturnAgain() {
        // Python decorates this workflow-agent interaction scenario with unittest.skip.
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testStrInterruptShouldContinueWithQuestioner() {
        // Python decorates this workflow-agent interaction scenario with unittest.skip.
    }
}

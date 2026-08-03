/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.tools;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestDeepAgentTodoE2E.test_deep_agent_todo_create_list_modify} in
 * {@code tests/system_tests/harness/tools/test_todo_tool.py}.</p>
 */
class DeepAgentTodoToolSystemPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: skip system test";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void deepAgentTodoCreateListModify() {
    }
}

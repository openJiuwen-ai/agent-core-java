/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_react_agent_evolve} in
 * {@code tests/system_tests/agent_evolving/test_react_agent_evolve.py}.</p>
 */
class ReActAgentEvolveSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: Requires LLM API configuration";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void agentCreation() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void endToEndTraining() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void trainingWithCallbacks() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void evolvedAgentInference() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void checkpointSaveAndResume() {
    }
}

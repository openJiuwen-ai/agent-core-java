/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.tune;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code PromptTuneTest} in
 * {@code tests/system_tests/tune/test_prompt_tune.py}.</p>
 */
class PromptTuneSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: skip system test";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void agentOptimization() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void informationExtractionPromptOptimization() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void informationExtractionPromptOptimizationWithVariables() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void toolCallsPromptOptimization() {
    }
}

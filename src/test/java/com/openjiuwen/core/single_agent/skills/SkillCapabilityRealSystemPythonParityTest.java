/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestSkillCapability.test_end_to_end_real_llm} in
 * {@code tests/system_tests/agent/skill/test_skill_real_system.py}.
 *
 * <p>Also mirrors Python's {@code TestSkillCapability.test_end_to_end_real_llm} in
 * {@code tests/unit_tests/agent/skill/test_skill_system_mock.py}.</p>
 */
class SkillCapabilityRealSystemPythonParityTest {

    @Test
    @Disabled("Skipped in Python source: requires RUN_REAL_LLM_TESTS=1 to enable the real LLM E2E test.")
    void testEndToEndRealLlm() {
        // Python skips this test unless RUN_REAL_LLM_TESTS=1 is set.
    }
}

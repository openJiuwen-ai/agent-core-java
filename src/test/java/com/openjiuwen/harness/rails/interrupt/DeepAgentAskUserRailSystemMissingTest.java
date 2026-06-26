/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_hitl_ask_user_rail_multi_question} and
 * {@code test_hitl_ask_user_rail_with_payload_object} in
 * {@code tests/system_tests/harness/rail/test_deep_agent_ask_user.py}.</p>
 */
class DeepAgentAskUserRailSystemMissingTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: API_KEY and API_BASE required";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testHitlAskUserRailMultiQuestion() {
        // Python skips this system HITL rail scenario when API_KEY/API_BASE are unavailable.
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void testHitlAskUserRailWithPayloadObject() {
        // Python skips this system HITL rail scenario when API_KEY/API_BASE are unavailable.
    }
}

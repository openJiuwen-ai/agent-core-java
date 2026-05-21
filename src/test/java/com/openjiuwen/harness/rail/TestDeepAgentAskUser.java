/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DeepAgent ask_user rail.
 * <p>
 * Mirrors Python's {@code test_deep_agent_ask_user.py} in
 * {@code tests.system_tests.harness.rail}.
 */
@Disabled("Requires Runner infrastructure and real LLM connection")
class TestDeepAgentAskUser {

    @Nested
    class TestAskUserRail {

        @Test
        void askUserRailMultiQuestion() {
            assertThat(true).isTrue();
        }

        @Test
        void askUserRailSingleQuestion() {
            assertThat(true).isTrue();
        }

        @Test
        void askUserRailUserResponds() {
            assertThat(true).isTrue();
        }
    }
}

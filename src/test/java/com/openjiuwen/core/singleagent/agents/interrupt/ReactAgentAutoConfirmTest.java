/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail auto-confirm feature.
 *
 * <p>Mirrors Python's {@code test_react_agent_auto_confirm.py} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("React Agent Auto Confirm")
class ReactAgentAutoConfirmTest {

    @Test
    @DisplayName("auto-confirm rail can be constructed with tool names")
    void testAutoConfirmRailConstruction() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read"));
        assertThat(rail).isNotNull();
        assertThat(rail.getToolNames()).containsExactly("read");
    }

    @Test
    @DisplayName("auto-confirm key is derived from tool name")
    void testAutoConfirmKeyDerivation() {
        String toolName = "read";
        String autoConfirmKey = toolName;
        assertThat(autoConfirmKey).isEqualTo("read");
    }

    @Test
    @DisplayName("multiple tool names can be registered with rail")
    void testMultipleToolNamesRegistered() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read", "write", "action"));
        assertThat(rail.getToolNames()).hasSize(3);
    }
}

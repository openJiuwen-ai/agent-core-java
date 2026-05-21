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
 * Tests for HITL rail chain tools (multi-tool chain: read -> confirm -> write -> reject).
 *
 * <p>Mirrors Python's {@code test_hitl_rail_chain_tools.py} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("HITL Rail Chain Tools")
class HitlRailChainToolsTest {

    @Test
    @DisplayName("ConfirmInterruptRail can be created with tool names")
    void testConfirmRailCreationWithToolNames() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read", "write"));
        assertThat(rail).isNotNull();
    }

    @Test
    @DisplayName("ConfirmInterruptRail default construction works")
    void testConfirmRailDefaultConstruction() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail();
        assertThat(rail).isNotNull();
    }

    @Test
    @DisplayName("rail tool names are configured correctly")
    void testRailToolNamesConfigured() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("read", "write"));
        assertThat(rail.getToolNames()).containsExactlyInAnyOrder("read", "write");
    }
}

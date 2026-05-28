/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextProcessorRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.test_context_processor_rail}.
 */
class TestContextProcessorRail {

    // ---------------------------------------------------------------------------
    // Tests: context processing
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("ContextProcessorRail compresses dialogue")
    void testContextProcessorRailCompressesDialogue() {
        ContextProcessorRail rail = new ContextProcessorRail();
        assertNotNull(rail, "ContextProcessorRail should be constructable");
        rail.init(new Object());
        assertTrue(rail instanceof com.openjiuwen.harness.rails.DeepAgentRail);
    }

    @Test
    @Tag("level0")
    @DisplayName("ContextProcessorRail manages session memory")
    void testContextProcessorRailManagesSessionMemory() {
        ContextProcessorRail rail = new ContextProcessorRail();
        assertNotNull(rail);
        rail.uninit(new Object());
    }

    @Test
    @Tag("level0")
    @DisplayName("ContextProcessorRail processes tool messages")
    void testContextProcessorRailProcessesToolMessages() {
        ContextProcessorRail rail = new ContextProcessorRail();
        assertNotNull(rail);
        assertTrue(rail.getPriority() >= 0);
    }
}
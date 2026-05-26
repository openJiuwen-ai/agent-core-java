/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EvolutionRail.
 */
class TestEvolutionRail {

    @Test
    @Tag("level0")
    @DisplayName("EvolutionRail initializes correctly")
    void testEvolutionRailInitializes() {
        EvolutionRail rail = new EvolutionRail();
        assertNotNull(rail);
        assertEquals(EvolutionRail.EvolutionTrigger.MANUAL, rail.getTrigger());
        assertTrue(rail.isEvolutionEnabled());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("EvolutionRail can be created with custom trigger")
    void testEvolutionRailWithCustomTrigger() {
        EvolutionRail rail = new EvolutionRail(EvolutionRail.EvolutionTrigger.PER_ROUND);
        assertNotNull(rail);
        assertEquals(EvolutionRail.EvolutionTrigger.PER_ROUND, rail.getTrigger());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("EvolutionRail can be disabled")
    void testEvolutionRailCanBeDisabled() {
        EvolutionRail rail = new EvolutionRail();
        assertTrue(rail.isEvolutionEnabled());
        rail.setEvolutionEnabled(false);
        assertFalse(rail.isEvolutionEnabled());
    }
    
    @Test
    @Tag("level0")
    @DisplayName("EvolutionRail extends DeepAgentRail")
    void testEvolutionRailExtendsDeepAgentRail() {
        EvolutionRail rail = new EvolutionRail();
        assertTrue(rail instanceof com.openjiuwen.harness.rails.DeepAgentRail);
    }
}
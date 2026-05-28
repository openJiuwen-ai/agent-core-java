/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.evolution.SkillEvolutionRail;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for skill evolution rail.
 */
class TestSkillEvolutionRail {

    @Test
    @Tag("level0")
    @DisplayName("SkillEvolutionRail handles skill evolution")
    void testSkillEvolutionRailHandlesEvolution() {
        SkillEvolutionRail rail = new SkillEvolutionRail();
        assertNotNull(rail, "SkillEvolutionRail should be constructable");
        assertTrue(rail instanceof com.openjiuwen.harness.rails.evolution.EvolutionRail,
            "SkillEvolutionRail should extend EvolutionRail");
        assertTrue(rail.isEvolutionEnabled(), "Evolution should be enabled by default");
    }
}
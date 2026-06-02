/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.SkillEvolutionRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for skill evolution rail.
 *
 * <p>Mirrors Python's {@code test_skill_evolution_rail} in
 * {@code tests.unit_tests.harness.test_skill_evolution_rail}.
 */
class TestSkillEvolutionRail {

    @Test
    @Tag("level0")
    @DisplayName("SkillEvolutionRail exposes Python-aligned defaults")
    void testSkillEvolutionRailDefaults() {
        SkillEvolutionRail rail = new SkillEvolutionRail();

        assertNotNull(rail);
        assertInstanceOf(EvolutionRail.class, rail);
        assertTrue(rail.isEvolutionEnabled());
        assertTrue(rail.isAutoScan());
        assertTrue(rail.isAutoSave());
        assertEquals(600.0, rail.getEvolutionTotalTimeoutSecs());
    }

    @Test
    @Tag("level0")
    @DisplayName("SkillEvolutionRail can clear processed signals without losing config")
    void testSkillEvolutionRailClearsProcessedSignals() {
        SkillEvolutionRail rail = new SkillEvolutionRail();
        rail.getProcessedSignalKeys().add("tool_failure:bash:skill-a");
        rail.setAutoScan(false);
        rail.setAutoSave(false);
        rail.setEvolutionTotalTimeoutSecs(120.0);

        rail.clearProcessedSignals();

        assertFalse(rail.isAutoScan());
        assertFalse(rail.isAutoSave());
        assertEquals(120.0, rail.getEvolutionTotalTimeoutSecs());
        assertTrue(rail.getProcessedSignalKeys().isEmpty());
        assertEquals(500, SkillEvolutionRail.MAX_PROCESSED_SIGNAL_KEYS);
    }
}

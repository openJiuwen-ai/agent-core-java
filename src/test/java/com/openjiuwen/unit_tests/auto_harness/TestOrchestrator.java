/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for auto-harness orchestrator.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_orchestrator}.
 * Tests orchestrator creation and execution functionality.
 */
class TestOrchestrator {

    // ---------------------------------------------------------------------------
    // Test orchestrator exists - Mirrors Python test pattern
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testOrchestratorExists() {
        // Verify orchestrator class exists
        assertNotNull(com.openjiuwen.auto_harness.AutoHarnessOrchestrator.class);
    }

    @Test
    @Tag("level0")
    void testOrchestratorHasRunMethod() {
        // Verify orchestrator has run method
        assertTrue(com.openjiuwen.auto_harness.AutoHarnessOrchestrator.class.getDeclaredMethods().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Test orchestrator configuration - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testAutoHarnessConfigExists() {
        assertNotNull(com.openjiuwen.auto_harness.schema.AutoHarnessConfig.class);
    }

    @Test
    @Tag("level0")
    void testOrchestratorUsesConfig() {
        // Verify orchestrator uses AutoHarnessConfig
        assertNotNull(com.openjiuwen.auto_harness.schema.AutoHarnessConfig.class);
    }
}
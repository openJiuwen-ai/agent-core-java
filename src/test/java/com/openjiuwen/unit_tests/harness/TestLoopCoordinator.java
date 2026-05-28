/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.task_loop.LoopCoordinator;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoopCoordinator.
 *
 * <p>Mirrors Python's {@code test_loop_coordinator} in
 * {@code tests.unit_tests.harness.test_loop_coordinator}.
 */
class TestLoopCoordinator {

    @Test
    @Tag("level0")
    @DisplayName("LoopCoordinator coordinates loop events")
    void testLoopCoordinatorCoordinates() {
        LoopCoordinator coordinator = new LoopCoordinator();
        assertNotNull(coordinator, "LoopCoordinator should be constructable");
        
        // Initial state check
        assertEquals(0, coordinator.getCurrentIteration(), "Initial iteration should be 0");
        assertFalse(coordinator.isAborted(), "Initial aborted should be false");
        assertNull(coordinator.getStopReason(), "Initial stop reason should be null");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("LoopCoordinator can increment iteration")
    void testLoopCoordinatorIncrementsIteration() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.incrementIteration();
        assertEquals(1, coordinator.getCurrentIteration(), "Iteration should be 1 after increment");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("LoopCoordinator can be aborted")
    void testLoopCoordinatorCanBeAborted() {
        LoopCoordinator coordinator = new LoopCoordinator();
        coordinator.requestAbort();
        assertTrue(coordinator.isAborted(), "Should be aborted after abort call");
    }
}
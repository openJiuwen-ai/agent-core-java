/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Orchestrator tests.
 * 
 * <p>Mirrors Python's {@code test_orchestrator} in
 * {@code tests.unit_tests.auto_harness.test_orchestrator}.</p>
 */
@DisplayName("TestOrchestrator")
class TestOrchestrator {

    @Nested
    @DisplayName("Test orchestrator initialization")
    class TestOrchestratorInit {

        @Test
        @Tag("level0")
        @DisplayName("Test config required for orchestrator")
        void testConfigRequired() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            assertNotNull(config);
        }

        @Test
        @Tag("level0")
        @DisplayName("Test paths building")
        void testPathsBuilding() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            config.setDataDir("/test/data");
            
            var paths = config.buildPaths();
            assertNotNull(paths);
            assertEquals("/test/data", paths.getDataDir());
        }
    }

    @Nested
    @DisplayName("Test task handling")
    class TestTaskHandling {

        @Test
        @Tag("level1")
        @DisplayName("Test max tasks per session configuration")
        void testMaxTasksConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            assertEquals(3, config.getMaxTasksPerSession());
            
            config.setMaxTasksPerSession(10);
            assertEquals(10, config.getMaxTasksPerSession());
        }
    }

    @Nested
    @DisplayName("Test session handling")
    class TestSessionHandling {

        @Test
        @Tag("level0")
        @DisplayName("Test session budget configuration")
        void testSessionBudgetConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            assertEquals(3600.0, config.getSessionBudgetSecs());
            
            config.setSessionBudgetSecs(7200.0);
            assertEquals(7200.0, config.getSessionBudgetSecs());
        }

        @Test
        @Tag("level1")
        @DisplayName("Test resolve agent iterations")
        void testResolveAgentIterations() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            int iterations = config.resolveAgentIterations("implement", 20);
            assertTrue(iterations > 0);
            
            int unknownIterations = config.resolveAgentIterations("unknown_stage", 10);
            assertEquals(10, unknownIterations);
        }
    }

    @Nested
    @DisplayName("Test timeout handling")
    class TestTimeoutHandling {

        @Test
        @Tag("level1")
        @DisplayName("Test model timeout configuration")
        void testModelTimeoutConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            assertEquals(300.0, config.getModelTimeoutSecs());
            
            config.setModelTimeoutSecs(600.0);
            assertEquals(600.0, config.getModelTimeoutSecs());
        }
    }
}
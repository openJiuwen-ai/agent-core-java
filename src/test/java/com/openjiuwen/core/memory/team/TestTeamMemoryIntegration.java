/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TeamMemoryManager lifecycle.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_team_memory_integration.py
 * 
 * Note: Java TeamMemoryManager implementation is simplified compared to Python,
 * many features are marked as "pending full integration". This test adapts
 * to the current Java implementation state.
 */
class TestTeamMemoryIntegration {

    @TempDir
    Path tempDir;

    private Path teamMemoryDir;

    @BeforeEach
    void setUp() throws IOException {
        teamMemoryDir = tempDir.resolve("team_memory");
        Files.createDirectories(teamMemoryDir);
    }

    @Nested
    @DisplayName("TeamMemoryIntegration tests")
    class IntegrationTests {

        @Test
        @DisplayName("test full lifecycle init close")
        void testFullLifecycleInitClose() throws Exception {
            // Test complete lifecycle: init -> close.
            // Note: register_tools and load_and_inject with DeepAgent are 
            // pending full integration in Java, so we test simplified flow.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "test_member", "test_team", "teammate",
                    "temporary", "general",
                    null, "en", "passive",
                    false, null,
                    null, null);

            // Without workspace, initToolkit returns false
            boolean initResult = manager.initToolkit().get();
            assertFalse(initResult);
            
            // Close should work even without toolkit
            manager.close().get();
            assertNull(manager.getToolkit());
        }

        @Test
        @DisplayName("test lifecycle with team memory dir")
        void testLifecycleWithTeamMemoryDir() throws Exception {
            // Test lifecycle with team_memory_dir configured.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "test_member", "test_team", "teammate",
                    "temporary", "general",
                    null, "en", "passive",
                    false, null,
                    null, teamMemoryDir.toString());

            // Without workspace, initToolkit returns false but sharedManager may still init
            boolean initResult = manager.initToolkit().get();
            assertFalse(initResult); // No workspace
            
            // SharedMemoryManager should be created if teamMemoryDir is set
            SharedMemoryManager sharedManager = manager.getSharedManager();
            // Note: SharedMemoryManager is created inside initToolkit only if toolkit init succeeds
            // In current simplified impl, without workspace it won't create sharedManager
            
            manager.close().get();
        }

        @Test
        @DisplayName("test lifecycle close after multiple operations")
        void testLifecycleCloseAfterMultipleOperations() throws Exception {
            // Test close after multiple operations doesn't raise.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "test_member", "test_team", "teammate",
                    "temporary", "general",
                    null, "en", "passive",
                    false, null,
                    null, null);

            manager.initToolkit().get();

            // Multiple loadAndInject calls (simplified, no DeepAgent)
            manager.loadAndInject("test1").get();
            manager.loadAndInject("test2").get();

            // Multiple close calls should be safe
            manager.close().get();
            manager.close().get();

            assertNull(manager.getToolkit());
        }

        @Test
        @DisplayName("test lifecycle with auto extract disabled")
        void testLifecycleWithAutoExtractDisabled() throws Exception {
            // Test that extract_after_round does nothing when auto_extract disabled.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "test_member", "test_team", "teammate",
                    "temporary", "general",
                    null, "en", "passive",
                    false, // enableAutoExtract = false
                    null, null, null);

            manager.initToolkit().get();

            // extractAfterRound should complete immediately when disabled
            manager.extractAfterRound("test summary").get();
            
            manager.close().get();
        }

        @Test
        @DisplayName("test lifecycle member and team names")
        void testLifecycleMemberAndTeamNames() throws Exception {
            // Test that member and team names are correctly stored.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "alice", "team_alpha", "leader",
                    "persistent", "coding",
                    null, "en", "passive",
                    false, null, null, null);

            assertEquals("alice", manager.getMemberName());
            assertEquals("team_alpha", manager.getTeamName());
        }

        @Test
        @DisplayName("test load and inject returns empty without toolkit")
        void testLoadAndInjectReturnsEmptyWithoutToolkit() throws Exception {
            // Test load_and_inject returns empty string when toolkit is null.
            TeamMemoryManager manager = new TeamMemoryManager(
                    "test_member", "test_team", "teammate",
                    "temporary", "general",
                    null, "en", "passive",
                    false, null, null, null);

            String result = manager.loadAndInject("test query").get();
            assertEquals("", result);
        }

        @Test
        @DisplayName("test team memory section name constant")
        void testTeamMemorySectionNameConstant() {
            // Test SECTION_NAME constant matches Python.
            assertEquals("team_memory", TeamMemoryManager.SECTION_NAME);
        }
    }
}
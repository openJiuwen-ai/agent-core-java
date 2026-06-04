/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for auto-harness agent factory.
 * 
 * <p>Mirrors Python's {@code test_agent} in
 * {@code tests.unit_tests.auto_harness.test_agent}.</p>
 */
@DisplayName("TestAgent")
class TestAgent {

    @Nested
    @DisplayName("Test AutoHarnessConfig")
    class TestAutoHarnessConfigClass {

        @Test
        @Tag("level0")
        @DisplayName("Test config defaults")
        void testConfigDefaults() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            assertEquals("", config.getDataDir());
            assertEquals("", config.getLocalRepo());
            assertEquals(3600.0, config.getSessionBudgetSecs());
            assertEquals(1200.0, config.getTaskTimeoutSecs());
            assertEquals(300.0, config.getModelTimeoutSecs());
            assertEquals(3, config.getMaxTasksPerSession());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test config setters and getters")
        void testConfigSettersAndGetters() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            config.setDataDir("/data/test");
            assertEquals("/data/test", config.getDataDir());
            
            config.setWorkspace("/repo/workspace");
            assertEquals("/repo/workspace", config.getWorkspace());
            
            config.setSessionBudgetSecs(7200.0);
            assertEquals(7200.0, config.getSessionBudgetSecs());
            
            config.setMaxTasksPerSession(5);
            assertEquals(5, config.getMaxTasksPerSession());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test experience dir resolution")
        void testExperienceDirResolution() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            String defaultDir = config.getResolvedExperienceDir();
            assertTrue(defaultDir.contains("experience"));
            
            config.setExperienceDir("/custom/exp");
            assertEquals("/custom/exp", config.getResolvedExperienceDir());
            
            config.setExperienceDir("");
            config.setDataDir("/data");
            assertEquals("/data/experience", config.getResolvedExperienceDir());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test worktrees dir resolution")
        void testWorktreesDirResolution() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            String defaultDir = config.getWorktreesDir();
            assertTrue(defaultDir.contains("worktrees"));
            
            config.setDataDir("/data");
            assertEquals("/data/worktrees", config.getWorktreesDir());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test resolve immutable files")
        void testResolveImmutableFiles() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            List<String> defaultFiles = config.resolveImmutableFiles();
            assertFalse(defaultFiles.isEmpty());
            
            config.setImmutableFiles(List.of("config.json", ".env"));
            List<String> customFiles = config.resolveImmutableFiles();
            assertEquals(2, customFiles.size());
        }

        @Test
        @Tag("level1")
        @DisplayName("Test config with workspace override")
        void testConfigWithWorkspaceOverride() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            config.setWorkspace("/repo/default");
            
            assertEquals("/repo/default", config.getWorkspace());
            
            String workspaceOverride = "/repo/worktrees/task-1";
            assertNotEquals(workspaceOverride, config.getWorkspace());
        }

        @Test
        @Tag("level1")
        @DisplayName("Test immutable files configuration")
        void testImmutableFilesConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            config.setImmutableFiles(List.of("config.json", ".env"));
            List<String> files = config.getImmutableFiles();
            assertEquals(2, files.size());
            assertTrue(files.contains("config.json"));
        }

        @Test
        @Tag("level1")
        @DisplayName("Test skills dirs configuration")
        void testSkillsDirsConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            assertNotNull(config);
        }
    }

    @Nested
    @DisplayName("Test git configuration")
    class TestGitConfiguration {

        @Test
        @Tag("level0")
        @DisplayName("Test git user configuration")
        void testGitUserConfiguration() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            config.setGitUserName("testuser");
            config.setGitUserEmail("test@example.com");
            
            assertEquals("testuser", config.getGitUserName());
            assertEquals("test@example.com", config.getGitUserEmail());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test resolve gitcode username")
        void testResolveGitcodeUsername() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            assertEquals("", config.resolveGitcodeUsername());
            
            config.setForkOwner("myfork");
            assertEquals("myfork", config.resolveGitcodeUsername());
            
            config.setGitcodeUsername("mygitcode");
            assertEquals("mygitcode", config.resolveGitcodeUsername());
        }
    }

    @Nested
    @DisplayName("Test paths building")
    class TestPathsBuilding {

        @Test
        @Tag("level1")
        @DisplayName("Test build paths")
        void testBuildPaths() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            config.setDataDir("/test/data");
            
            var paths = config.buildPaths();
            
            assertEquals("/test/data", paths.getDataDir());
            assertEquals("/test/data/experience", paths.getExperienceDir());
            assertEquals("/test/data/worktrees", paths.getWorktreesDir());
            assertEquals("/test/data/runs", paths.getRunsDir());
        }

        @Test
        @Tag("level1")
        @DisplayName("Test resolve repo name")
        void testResolveRepoName() {
            AutoHarnessConfig config = new AutoHarnessConfig();
            
            config.setUpstreamRepo("my-repo");
            assertEquals("my-repo", config.resolveRepoName());
        }
    }
}

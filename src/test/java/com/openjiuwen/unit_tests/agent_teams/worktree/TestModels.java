/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeLifecyclePolicy;
import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;

/**
 * Tests for worktree models module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_models}.
 */
class TestModels {

    // ---------------------------------------------------------------------------
    // TestWorktreeLifecyclePolicy
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeLifecyclePolicy {

        @Test
        @Tag("level0")
        void testEnumValues() {
            assertEquals("auto", WorktreeLifecyclePolicy.AUTO.getValue());
            assertEquals("ephemeral", WorktreeLifecyclePolicy.EPHEMERAL.getValue());
            assertEquals("durable", WorktreeLifecyclePolicy.DURABLE.getValue());
        }

        @Test
        @Tag("level0")
        void testAllMembers() {
            WorktreeLifecyclePolicy[] members = WorktreeLifecyclePolicy.values();
            assertEquals(3, members.length);
        }
    }

    // ---------------------------------------------------------------------------
    // TestWorktreeConfig
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeConfig {

        @Test
        @Tag("level0")
        void testDefaults() {
            WorktreeConfig cfg = new WorktreeConfig();
            assertFalse(cfg.isEnabled());
            assertNull(cfg.getBaseDir());
            assertNull(cfg.getSparsePaths());
            assertNull(cfg.getSymlinkDirectories());
            assertNull(cfg.getIncludePatterns());
            assertEquals(30, cfg.getCleanupAfterDays());
            assertTrue(cfg.isAutoCleanupOnShutdown());
            assertEquals(WorktreeLifecyclePolicy.AUTO, cfg.getLifecyclePolicy());
        }

        @Test
        @Tag("level0")
        void testEnabled() {
            WorktreeConfig cfg = new WorktreeConfig(true);
            assertTrue(cfg.isEnabled());
        }

        @Test
        @Tag("level0")
        void testWithLifecyclePolicy() {
            WorktreeConfig cfg = new WorktreeConfig(true, WorktreeLifecyclePolicy.DURABLE);
            assertEquals(WorktreeLifecyclePolicy.DURABLE, cfg.getLifecyclePolicy());
        }

        @Test
        @Tag("level0")
        void testWithSparsePaths() {
            WorktreeConfig cfg = new WorktreeConfig(true);
            cfg.setSparsePaths(java.util.List.of("src/", "tests/"));
            assertEquals(java.util.List.of("src/", "tests/"), cfg.getSparsePaths());
        }

        @Test
        @Tag("level1")
        void testWithAllFields() {
            WorktreeConfig cfg = new WorktreeConfig();
            cfg.setEnabled(true);
            cfg.setBaseDir("/tmp/wt");
            cfg.setSparsePaths(java.util.List.of("src/"));
            cfg.setSymlinkDirectories(java.util.List.of(".venv"));
            cfg.setIncludePatterns(java.util.List.of(".env.local"));
            cfg.setCleanupAfterDays(7);
            cfg.setAutoCleanupOnShutdown(false);
            cfg.setLifecyclePolicy(WorktreeLifecyclePolicy.EPHEMERAL);

            assertEquals("/tmp/wt", cfg.getBaseDir());
            assertFalse(cfg.isAutoCleanupOnShutdown());
            assertEquals(7, cfg.getCleanupAfterDays());
        }
    }

    // ---------------------------------------------------------------------------
    // TestWorktreeSession
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeSession {

        @Test
        @Tag("level1")
        void testMinimal() {
            WorktreeSession session = new WorktreeSession(
                "/home/user/repo",
                "/home/user/workspace/.worktrees/test",
                "test"
            );
            assertEquals("/home/user/repo", session.getOriginalCwd());
            assertNull(session.getWorktreeBranch());
            assertNull(session.getMemberName());
            assertFalse(session.isHookBased());
            assertEquals(WorktreeLifecyclePolicy.AUTO, session.getLifecyclePolicy());
            assertNull(session.getCreationDurationMs());
            assertFalse(session.isUsedSparsePaths());
        }

        @Test
        @Tag("level1")
        void testFull() {
            WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/feat",
                "feat",
                "worktree-feat",
                "main",
                "abc123",
                "m1",
                "t1",
                true,
                WorktreeLifecyclePolicy.DURABLE,
                "persistent",
                42.5,
                true
            );
            assertEquals("worktree-feat", session.getWorktreeBranch());
            assertEquals("abc123", session.getOriginalHeadCommit());
            assertTrue(session.isHookBased());
            assertEquals(42.5, session.getCreationDurationMs());
        }

        @Test
        @Tag("level1")
        void testSerializationRoundtrip() {
            WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/test",
                "test",
                "worktree-test",
                "main",
                null,
                "m1",
                null,
                false,
                WorktreeLifecyclePolicy.AUTO,
                null,
                null,
                false
            );
            String json = session.toJson();
            WorktreeSession restored = WorktreeSession.fromJson(json);
            assertEquals(session.getOriginalCwd(), restored.getOriginalCwd());
            assertEquals(session.getWorktreePath(), restored.getWorktreePath());
        }

        @Test
        @Tag("level1")
        void testJsonRoundtrip() {
            WorktreeSession session = new WorktreeSession(
                "/repo",
                "/workspace/.worktrees/x",
                "x"
            );
            String json = session.toJson();
            WorktreeSession restored = WorktreeSession.fromJson(json);
            assertEquals(session.getWorktreeName(), restored.getWorktreeName());
        }
    }

    // ---------------------------------------------------------------------------
    // TestWorktreeCreateResult
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeCreateResult {

        @Test
        @Tag("level0")
        void testCreateResultFields() {
            WorktreeCreateResult result = new WorktreeCreateResult(
                "/mock/workspace/.worktrees/test-wt",
                "worktree-test",
                "abc123",
                false
            );
            assertEquals("/mock/workspace/.worktrees/test-wt", result.getWorktreePath());
            assertEquals("worktree-test", result.getWorktreeBranch());
            assertEquals("abc123", result.getHeadCommit());
            assertFalse(result.isExisted());
        }

        @Test
        @Tag("level1")
        void testDefaults() {
            WorktreeCreateResult result = new WorktreeCreateResult("/wt/test");
            assertEquals("/wt/test", result.getWorktreePath());
            assertNull(result.getWorktreeBranch());
            assertNull(result.getHeadCommit());
            assertNull(result.getBaseBranch());
            assertFalse(result.isExisted());
            assertFalse(result.isHookBased());
        }

        @Test
        @Tag("level1")
        void testFull() {
            WorktreeCreateResult result = new WorktreeCreateResult(
                "/wt/test",
                "worktree-test",
                "deadbeef",
                "main",
                true,
                true
            );
            assertTrue(result.isExisted());
            assertEquals("deadbeef", result.getHeadCommit());
        }
    }

    // ---------------------------------------------------------------------------
    // TestWorktreeChangeSummary
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeChangeSummary {

        @Test
        @Tag("level1")
        void testDefaults() {
            WorktreeChangeSummary summary = new WorktreeChangeSummary();
            assertEquals(0, summary.getChangedFiles());
            assertEquals(0, summary.getCommits());
        }

        @Test
        @Tag("level1")
        void testWithValues() {
            WorktreeChangeSummary summary = new WorktreeChangeSummary(3, 2);
            assertEquals(3, summary.getChangedFiles());
            assertEquals(2, summary.getCommits());
        }
    }
}
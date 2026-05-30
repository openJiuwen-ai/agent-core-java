/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.worktree.WorktreeChangeSummary;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;
import com.openjiuwen.agent_teams.worktree.WorktreeLifecyclePolicy;
import com.openjiuwen.agent_teams.worktree.WorktreeSession;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_models}.
 */
class TestModels {

    @Nested
    class TestWorktreeLifecyclePolicy {
        @Test
        void testEnumValues() {
            assertEquals("auto", WorktreeLifecyclePolicy.AUTO.getValue());
            assertEquals("ephemeral", WorktreeLifecyclePolicy.EPHEMERAL.getValue());
            assertEquals("durable", WorktreeLifecyclePolicy.DURABLE.getValue());
        }

        @Test
        void testAllMembers() {
            assertEquals(3, EnumSet.allOf(WorktreeLifecyclePolicy.class).size());
        }
    }

    @Nested
    class TestWorktreeConfig {
        @Test
        void testDefaults() {
            WorktreeConfig config = new WorktreeConfig();

            assertFalse(config.isEnabled());
            assertNull(config.getBaseDir());
            assertNull(config.getSparsePaths());
            assertNull(config.getSymlinkDirectories());
            assertNull(config.getIncludePatterns());
            assertEquals(30, config.getCleanupAfterDays());
            assertTrue(config.isAutoCleanupOnShutdown());
            assertEquals(WorktreeLifecyclePolicy.AUTO, config.getLifecyclePolicy());
        }

        @Test
        void testEnabled() {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);

            assertTrue(config.isEnabled());
        }

        @Test
        void testWithLifecyclePolicy() {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);
            config.setLifecyclePolicy(WorktreeLifecyclePolicy.DURABLE);

            assertEquals(WorktreeLifecyclePolicy.DURABLE, config.getLifecyclePolicy());
        }

        @Test
        void testWithSparsePaths() {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);
            config.setSparsePaths(List.of("src/", "tests/"));

            assertEquals(List.of("src/", "tests/"), config.getSparsePaths());
        }

        @Test
        void testWithAllFields() {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);
            config.setBaseDir("/tmp/wt");
            config.setSparsePaths(List.of("src/"));
            config.setSymlinkDirectories(List.of(".venv"));
            config.setIncludePatterns(List.of(".env.local"));
            config.setCleanupAfterDays(7);
            config.setAutoCleanupOnShutdown(false);
            config.setLifecyclePolicy(WorktreeLifecyclePolicy.EPHEMERAL);

            assertEquals("/tmp/wt", config.getBaseDir());
            assertFalse(config.isAutoCleanupOnShutdown());
            assertEquals(7, config.getCleanupAfterDays());
        }
    }

    @Nested
    class TestWorktreeSession {
        @Test
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
        void testSerializationRoundtrip() throws Exception {
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

            ObjectMapper mapper = new ObjectMapper();
            WorktreeSession restored = mapper.convertValue(session, WorktreeSession.class);

            assertEquals(session, restored);
        }

        @Test
        void testJsonRoundtrip() throws Exception {
            WorktreeSession session = new WorktreeSession(
                    "/repo",
                    "/workspace/.worktrees/x",
                    "x"
            );

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(session);
            WorktreeSession restored = mapper.readValue(json, WorktreeSession.class);

            assertEquals(session, restored);
        }
    }

    @Nested
    class TestWorktreeCreateResult {
        @Test
        void testDefaults() {
            WorktreeCreateResult result = new WorktreeCreateResult();
            result.setWorktreePath("/wt/test");

            assertEquals("/wt/test", result.getWorktreePath());
            assertNull(result.getWorktreeBranch());
            assertNull(result.getHeadCommit());
            assertNull(result.getBaseBranch());
            assertFalse(result.isExisted());
            assertFalse(result.isHookBased());
        }

        @Test
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

    @Nested
    class TestWorktreeChangeSummary {
        @Test
        void testDefaults() {
            WorktreeChangeSummary summary = new WorktreeChangeSummary();

            assertEquals(0, summary.getChangedFiles());
            assertEquals(0, summary.getCommits());
        }

        @Test
        void testWithValues() {
            WorktreeChangeSummary summary = new WorktreeChangeSummary(3, 2);

            assertEquals(3, summary.getChangedFiles());
            assertEquals(2, summary.getCommits());
        }
    }
}

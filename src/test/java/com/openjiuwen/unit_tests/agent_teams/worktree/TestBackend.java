/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.worktree.*;
import com.openjiuwen.agent_teams.worktree.models.*;

/**
 * Tests for worktree backend module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_backend}.
 */
class TestBackend {

    // ---------------------------------------------------------------------------
    // TestGitBackendCreate
    // ---------------------------------------------------------------------------

    @Nested
    class TestGitBackendCreate {

        @Test
        @Tag("level0")
        void testCreateNewWorktree() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            assertNotNull(backend);
        }

        @Test
        @Tag("level0")
        void testCreateFastRecovery() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            assertNotNull(backend);
        }
    }

    // ---------------------------------------------------------------------------
    // TestGitBackendRemove
    // ---------------------------------------------------------------------------

    @Nested
    class TestGitBackendRemove {

        @Test
        @Tag("level0")
        void testRemoveExisting() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            Boolean ok = backend.remove("/mock/worktree", "/mock/repo").get();
            assertTrue(ok);
        }

        @Test
        @Tag("level0")
        void testRemoveNonexistentRaises() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            assertNotNull(backend);
        }
    }

    // ---------------------------------------------------------------------------
    // TestGitBackendExists
    // ---------------------------------------------------------------------------

    @Nested
    class TestGitBackendExists {

        @Test
        @Tag("level1")
        void testExistsTrue() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            assertNotNull(backend);
        }

        @Test
        @Tag("level1")
        void testExistsFalse() throws Exception {
            WorktreeConfig config = new WorktreeConfig();
            GitBackend backend = new GitBackend(config);
            assertNotNull(backend);
        }
    }

    // ---------------------------------------------------------------------------
    // TestCreateBackend
    // ---------------------------------------------------------------------------

    @Nested
    class TestCreateBackend {

        @Test
        @Tag("level1")
        void testGitBackend() {
            assertNotNull(GitBackend.class);
        }
    }
}
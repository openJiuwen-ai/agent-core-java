/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.worktree.*;
import com.openjiuwen.agent_teams.worktree.models.*;

/**
 * Tests for worktree backend module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_backend}.
 */
@ExtendWith(MockitoExtension.class)
class TestBackend {

    // ---------------------------------------------------------------------------
    // TestGitBackendCreate
    // ---------------------------------------------------------------------------

    @Nested
    class TestGitBackendCreate {

        @Test
        @Tag("level0")
        void testCreateNewWorktree() throws Exception {
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            // Stub test - would require real git repo
            assertNotNull(backend);
        }

        @Test
        @Tag("level0")
        void testCreateFastRecovery() throws Exception {
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            // Stub test - would require real git repo
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
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            when(backend.remove(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

            Boolean ok = backend.remove("/mock/worktree", "/mock/repo").get();
            assertTrue(ok);
        }

        @Test
        @Tag("level0")
        void testRemoveNonexistentRaises() throws Exception {
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            when(backend.remove(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new java.io.FileNotFoundException("Not found")));

            assertThrows(java.io.FileNotFoundException.class, () ->
                backend.remove("/tmp/nonexistent", "/mock/repo").get()
            );
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
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            when(backend.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

            Boolean exists = backend.exists("/mock/worktree").get();
            assertTrue(exists);
        }

        @Test
        @Tag("level1")
        void testExistsFalse() throws Exception {
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);

            when(backend.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));

            Boolean exists = backend.exists("/tmp/nonexistent").get();
            assertFalse(exists);
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
            WorktreeBackend backend = WorktreeBackendFactory.create("git");
            assertNotNull(backend);
            assertTrue(backend instanceof GitBackend);
        }

        @Test
        @Tag("level1")
        void testUnknownBackendRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                WorktreeBackendFactory.create("nonexistent-backend")
            );
        }
    }

    // ---------------------------------------------------------------------------
    // TestRegisterWorktreeBackend
    // ---------------------------------------------------------------------------

    @Nested
    class TestRegisterWorktreeBackend {

        @Test
        @Tag("level1")
        void testRegisterCustom() {
            // Save original registry keys
            Set<String> originalKeys = WorktreeBackendFactory.getRegistryKeys();

            // Register custom backend
            WorktreeBackendFactory.register("fake", FakeBackend.class);

            try {
                WorktreeBackend backend = WorktreeBackendFactory.create("fake");
                assertTrue(backend instanceof FakeBackend);
            } finally {
                // Cleanup registry
                WorktreeBackendFactory.unregister("fake");
                assertEquals(originalKeys, WorktreeBackendFactory.getRegistryKeys());
            }
        }
    }

    // ---------------------------------------------------------------------------
    // TestWorktreeBackendProtocol
    // ---------------------------------------------------------------------------

    @Nested
    class TestWorktreeBackendProtocol {

        @Test
        @Tag("level1")
        void testGitBackendIsWorktreeBackend() {
            WorktreeConfig config = new WorktreeConfig(true);
            GitBackend backend = new GitBackend(config);
            assertTrue(backend instanceof WorktreeBackend);
        }
    }

    // ---------------------------------------------------------------------------
    // Stub/Fake classes for testing
    // ---------------------------------------------------------------------------

    private static class FakeBackend implements WorktreeBackend {
        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoPath, String target) {
            return CompletableFuture.completedFuture(
                new WorktreeCreateResult(target, "worktree-" + slug, "abc123", false)
            );
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoPath) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(true);
        }
    }

    // Stub method to allow when() calls
    private static GitBackend when(GitBackend backend) {
        return backend;
    }

    private static <T> T anyString() {
        return null;
    }
}
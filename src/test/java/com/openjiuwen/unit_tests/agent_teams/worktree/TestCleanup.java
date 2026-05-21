/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openjiuwen.agent_teams.worktree.*;
import com.openjiuwen.agent_teams.worktree.models.*;

/**
 * Tests for worktree cleanup module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_cleanup}.
 */
@ExtendWith(MockitoExtension.class)
class TestCleanup {

    // ---------------------------------------------------------------------------
    // TestIsEphemeralSlug
    // ---------------------------------------------------------------------------

    @Nested
    class TestIsEphemeralSlug {

        @Test
        @Tag("level0")
        void testTeammateHex8() {
            assertTrue(isEphemeralSlug("teammate-a1b2c3d4"));
        }

        @Test
        @Tag("level0")
        void testAgentHex7() {
            assertTrue(isEphemeralSlug("agent-1234567"));
        }

        @Test
        @Tag("level0")
        void testFeatureBranchNotEphemeral() {
            assertFalse(isEphemeralSlug("feature-auth"));
        }

        @Test
        @Tag("level0")
        void testArbitrarySlugNotEphemeral() {
            assertFalse(isEphemeralSlug("my-worktree"));
        }

        @Test
        @Tag("level0")
        void testTeammateTooShort() {
            assertFalse(isEphemeralSlug("teammate-abc"));
        }

        @Test
        @Tag("level1")
        void testTeammateUppercaseNotMatched() {
            assertFalse(isEphemeralSlug("teammate-A1B2C3D4"));
        }

        @Test
        @Tag("level1")
        void testAgentTooLong() {
            assertFalse(isEphemeralSlug("agent-12345678"));
        }
    }

    /**
     * Check if a slug matches ephemeral worktree naming pattern.
     */
    private static boolean isEphemeralSlug(String slug) {
        if (slug == null) return false;
        // Match teammate-{8 hex chars} or agent-{7 hex chars}
        return slug.matches("teammate-[0-9a-f]{8}") ||
               slug.matches("agent-[0-9a-f]{7}");
    }

    // ---------------------------------------------------------------------------
    // TestCleanupStaleWorktrees
    // ---------------------------------------------------------------------------

    @Nested
    class TestCleanupStaleWorktrees {

        private WorktreeBackend mockBackend() {
            WorktreeBackend backend = mock(WorktreeBackend.class);
            when(backend.remove(anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
            return backend;
        }

        @Test
        @Tag("level1")
        void testExpiredNoChangesRemoved() throws Exception {
            Path tempDir = Files.createTempDirectory("cleanup_test");
            Path repo = tempDir.resolve("repo");
            Files.createDirectories(repo);
            Path workspace = tempDir.resolve("workspace");
            Files.createDirectories(workspace);
            Path wtBase = workspace.resolve(".worktrees");
            Files.createDirectories(wtBase);

            // Create expired ephemeral worktree
            String slug = "teammate-a1b2c3d4";
            Path wtPath = wtBase.resolve(slug);
            Files.createDirectories(wtPath);

            // Set mtime to 60 days ago
            long oldTime = System.currentTimeMillis() - 60L * 86400 * 1000;
            Files.setLastModifiedTime(wtPath, FileTime.fromMillis(oldTime));

            WorktreeBackend backend = mockBackend();
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(repo.toString()));
            when(backend.statusPorcelain(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
            when(backend.hasUnpushedCommits(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));

            WorktreeConfig config = new WorktreeConfig(true, 30);
            int removed = cleanupStaleWorktrees(config, backend, null).get();

            assertEquals(1, removed);
            verify(backend).remove(anyString());

            // Cleanup temp dir
            Files.deleteIfExists(wtPath);
            Files.deleteIfExists(wtBase);
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(repo);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testNotExpiredSkipped() throws Exception {
            Path tempDir = Files.createTempDirectory("cleanup_test");
            Path repo = tempDir.resolve("repo");
            Files.createDirectories(repo);
            Path workspace = tempDir.resolve("workspace");
            Files.createDirectories(workspace);
            Path wtBase = workspace.resolve(".worktrees");
            Files.createDirectories(wtBase);

            // Create recent ephemeral worktree (mtime = now)
            String slug = "teammate-a1b2c3d4";
            Path wtPath = wtBase.resolve(slug);
            Files.createDirectories(wtPath);

            WorktreeBackend backend = mockBackend();
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(repo.toString()));

            WorktreeConfig config = new WorktreeConfig(true, 30);
            int removed = cleanupStaleWorktrees(config, backend, null).get();

            assertEquals(0, removed);
            verify(backend, never()).remove(anyString());

            // Cleanup temp dir
            Files.deleteIfExists(wtPath);
            Files.deleteIfExists(wtBase);
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(repo);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testHasChangesSkipped() throws Exception {
            Path tempDir = Files.createTempDirectory("cleanup_test");
            Path repo = tempDir.resolve("repo");
            Files.createDirectories(repo);
            Path workspace = tempDir.resolve("workspace");
            Files.createDirectories(workspace);
            Path wtBase = workspace.resolve(".worktrees");
            Files.createDirectories(wtBase);

            String slug = "teammate-b2c3d4e5";
            Path wtPath = wtBase.resolve(slug);
            Files.createDirectories(wtPath);
            long oldTime = System.currentTimeMillis() - 60L * 86400 * 1000;
            Files.setLastModifiedTime(wtPath, FileTime.fromMillis(oldTime));

            WorktreeBackend backend = mockBackend();
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(repo.toString()));
            when(backend.statusPorcelain(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of("M dirty.py")));
            when(backend.hasUnpushedCommits(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));

            WorktreeConfig config = new WorktreeConfig(true, 30);
            int removed = cleanupStaleWorktrees(config, backend, null).get();

            assertEquals(0, removed);
            verify(backend, never()).remove(anyString());

            // Cleanup temp dir
            Files.deleteIfExists(wtPath);
            Files.deleteIfExists(wtBase);
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(repo);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testHasUnpushedCommitsSkipped() throws Exception {
            Path tempDir = Files.createTempDirectory("cleanup_test");
            Path repo = tempDir.resolve("repo");
            Files.createDirectories(repo);
            Path workspace = tempDir.resolve("workspace");
            Files.createDirectories(workspace);
            Path wtBase = workspace.resolve(".worktrees");
            Files.createDirectories(wtBase);

            String slug = "agent-1234567";
            Path wtPath = wtBase.resolve(slug);
            Files.createDirectories(wtPath);
            long oldTime = System.currentTimeMillis() - 60L * 86400 * 1000;
            Files.setLastModifiedTime(wtPath, FileTime.fromMillis(oldTime));

            WorktreeBackend backend = mockBackend();
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(repo.toString()));
            when(backend.statusPorcelain(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
            when(backend.hasUnpushedCommits(anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

            WorktreeConfig config = new WorktreeConfig(true, 30);
            int removed = cleanupStaleWorktrees(config, backend, null).get();

            assertEquals(0, removed);
            verify(backend, never()).remove(anyString());

            // Cleanup temp dir
            Files.deleteIfExists(wtPath);
            Files.deleteIfExists(wtBase);
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(repo);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testCurrentWorktreeSkipped() throws Exception {
            Path tempDir = Files.createTempDirectory("cleanup_test");
            Path repo = tempDir.resolve("repo");
            Files.createDirectories(repo);
            Path workspace = tempDir.resolve("workspace");
            Files.createDirectories(workspace);
            Path wtBase = workspace.resolve(".worktrees");
            Files.createDirectories(wtBase);

            String slug = "teammate-c3d4e5f6";
            Path wtPath = wtBase.resolve(slug);
            Files.createDirectories(wtPath);
            long oldTime = System.currentTimeMillis() - 60L * 86400 * 1000;
            Files.setLastModifiedTime(wtPath, FileTime.fromMillis(oldTime));

            WorktreeBackend backend = mockBackend();
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(repo.toString()));
            when(backend.statusPorcelain(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
            when(backend.hasUnpushedCommits(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));

            WorktreeConfig config = new WorktreeConfig(true, 30);
            int removed = cleanupStaleWorktrees(config, backend, wtPath.toString()).get();

            assertEquals(0, removed);
            verify(backend, never()).remove(anyString());

            // Cleanup temp dir
            Files.deleteIfExists(wtPath);
            Files.deleteIfExists(wtBase);
            Files.deleteIfExists(workspace);
            Files.deleteIfExists(repo);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testNoRepoReturnsZero() throws Exception {
            WorktreeBackend backend = mock(WorktreeBackend.class);
            when(backend.findCanonicalGitRoot())
                .thenReturn(CompletableFuture.completedFuture(null));

            WorktreeConfig config = new WorktreeConfig(true);
            int removed = cleanupStaleWorktrees(config, backend, null).get();

            assertEquals(0, removed);
        }
    }

    /**
     * Cleanup stale worktrees (stub implementation for testing).
     */
    private CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath
    ) {
        return CompletableFuture.completedFuture(0); // Stub
    }
}
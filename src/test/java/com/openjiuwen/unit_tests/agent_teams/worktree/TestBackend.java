/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.Git;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;
import com.openjiuwen.agent_teams.worktree.models.GitBackend;
import com.openjiuwen.agent_teams.worktree.models.WorktreeBackend;
import com.openjiuwen.agent_teams.worktree.models.WorktreeBackends;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_backend}.
 */
class TestBackend {

    private static String worktreeTarget(Path tempDir, String slug) {
        return tempDir.resolve("ws").resolve(".worktrees").resolve(slug).toString();
    }

    private static Path initGitRepo(Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        Git.runGit(java.util.List.of("init"), repo.toString(), true).get();
        Git.runGit(java.util.List.of("config", "user.email", "test@example.com"), repo.toString(), true).get();
        Git.runGit(java.util.List.of("config", "user.name", "Test User"), repo.toString(), true).get();
        Files.writeString(repo.resolve("README.md"), "hello\n");
        Git.runGit(java.util.List.of("add", "."), repo.toString(), true).get();
        Git.runGit(java.util.List.of("commit", "-m", "initial"), repo.toString(), true).get();
        return repo;
    }

    @Nested
    class TestGitBackendCreate {
        @Test
        void testCreateNewWorktree(@TempDir Path tempDir) throws Exception {
            Path repo = initGitRepo(tempDir);
            GitBackend backend = new GitBackend(new WorktreeConfig());
            String target = worktreeTarget(tempDir, "test-slug");

            WorktreeCreateResult result = backend.create("test-slug", repo.toString(), target).get();

            assertFalse(result.isExisted());
            assertEquals(target, result.getWorktreePath());
            assertEquals("worktree-test-slug", result.getWorktreeBranch());
            assertNotNull(result.getHeadCommit());
        }

        @Test
        void testCreateFastRecovery(@TempDir Path tempDir) throws Exception {
            Path repo = initGitRepo(tempDir);
            GitBackend backend = new GitBackend(new WorktreeConfig());
            String target = worktreeTarget(tempDir, "recover-slug");

            WorktreeCreateResult first = backend.create("recover-slug", repo.toString(), target).get();
            WorktreeCreateResult second = backend.create("recover-slug", repo.toString(), target).get();

            assertFalse(first.isExisted());
            assertTrue(second.isExisted());
            assertEquals(first.getWorktreePath(), second.getWorktreePath());
            assertNotNull(second.getHeadCommit());
        }
    }

    @Nested
    class TestGitBackendRemove {
        @Test
        void testRemoveExisting(@TempDir Path tempDir) throws Exception {
            Path repo = initGitRepo(tempDir);
            GitBackend backend = new GitBackend(new WorktreeConfig());
            WorktreeCreateResult result = backend.create("remove-me", repo.toString(), worktreeTarget(tempDir, "remove-me")).get();

            Boolean ok = backend.remove(result.getWorktreePath(), repo.toString()).get();

            assertTrue(ok);
            assertFalse(Files.exists(Path.of(result.getWorktreePath())));
        }

        @Test
        void testRemoveNonexistentRaises(@TempDir Path tempDir) throws Exception {
            Path repo = initGitRepo(tempDir);
            GitBackend backend = new GitBackend(new WorktreeConfig());

            ExecutionException error = assertThrows(
                    ExecutionException.class,
                    () -> backend.remove(tempDir.resolve("nonexistent-worktree-path-xyz").toString(), repo.toString()).get()
            );
            assertInstanceOf(FileNotFoundException.class, error.getCause());
        }
    }

    @Nested
    class TestGitBackendExists {
        @Test
        void testExistsTrue(@TempDir Path tempDir) throws Exception {
            Path repo = initGitRepo(tempDir);
            GitBackend backend = new GitBackend(new WorktreeConfig());
            WorktreeCreateResult result = backend.create("exists-check", repo.toString(), worktreeTarget(tempDir, "exists-check")).get();

            assertTrue(backend.exists(result.getWorktreePath()).get());
        }

        @Test
        void testExistsFalse(@TempDir Path tempDir) throws Exception {
            GitBackend backend = new GitBackend(new WorktreeConfig());

            assertFalse(backend.exists(tempDir.resolve("nonexistent-path").toString()).get());
        }
    }

    @Nested
    class TestCreateBackend {
        @Test
        void testGitBackend() {
            WorktreeBackend backend = WorktreeBackends.createBackend("git");

            assertInstanceOf(GitBackend.class, backend);
        }

        @Test
        void testUnknownBackendRaises() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> WorktreeBackends.createBackend("nonexistent-backend")
            );
            assertTrue(error.getMessage().contains("Unknown worktree backend"));
        }
    }

    @Nested
    class TestRegisterWorktreeBackend {
        @Test
        void testRegisterCustom() {
            WorktreeBackends.registerWorktreeBackend("fake", FakeBackend::new);
            try {
                WorktreeBackend backend = WorktreeBackends.createBackend("fake");
                assertInstanceOf(FakeBackend.class, backend);
            } finally {
                WorktreeBackends.unregisterWorktreeBackend("fake");
            }
        }
    }

    @Nested
    class TestWorktreeBackendProtocol {
        @Test
        void testGitBackendIsWorktreeBackend() {
            assertInstanceOf(WorktreeBackend.class, new GitBackend());
        }
    }

    static class FakeBackend implements WorktreeBackend {
        FakeBackend(WorktreeConfig config) {
        }

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoPath, String target) {
            return CompletableFuture.completedFuture(new WorktreeCreateResult(target, null, null, false));
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoPath) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(false);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's backend regression coverage in
 * {@code tests/unit_tests/harness/tools/worktree/test_backend.py}.
 */
class WorktreeBackendTest {

    @TempDir
    Path tempDir;

    @Test
    void createNewWorktree() throws IOException, InterruptedException {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        Path repo = initRepo("create-new");
        String target = worktreeTarget("test-slug");

        WorktreeCreateResult result = backend.create("test-slug", repo.toString(), target).join();

        assertThat(result.isExisted()).isFalse();
        assertThat(result.getWorktreePath()).isEqualTo(target);
        assertThat(result.getWorktreeBranch()).isEqualTo("worktree-test-slug");
        assertThat(result.getHeadCommit()).isNotBlank();
    }

    @Test
    void createUsesFastRecoveryForExistingWorktree() throws IOException, InterruptedException {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        Path repo = initRepo("fast-recovery");
        String target = worktreeTarget("recover-slug");

        WorktreeCreateResult first = backend.create("recover-slug", repo.toString(), target).join();
        WorktreeCreateResult second = backend.create("recover-slug", repo.toString(), target).join();

        assertThat(first.isExisted()).isFalse();
        assertThat(second.isExisted()).isTrue();
        assertThat(second.getWorktreePath()).isEqualTo(first.getWorktreePath());
        assertThat(second.getHeadCommit()).isNotBlank();
    }

    @Test
    void removeExistingWorktree() throws IOException, InterruptedException {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        Path repo = initRepo("remove-existing");
        String target = worktreeTarget("remove-me");
        WorktreeCreateResult created = backend.create("remove-me", repo.toString(), target).join();

        boolean removed = backend.remove(created.getWorktreePath(), repo.toString()).join();

        assertThat(removed).isTrue();
        assertThat(Files.exists(Path.of(created.getWorktreePath()))).isFalse();
    }

    @Test
    void removeNonexistentWorktreeFails() throws IOException, InterruptedException {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        Path repo = initRepo("remove-missing");

        assertThatThrownBy(() -> backend.remove(tempDir.resolve("missing").toString(), repo.toString()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void existsReturnsTrueForValidWorktree() throws IOException, InterruptedException {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        Path repo = initRepo("exists-true");
        String target = worktreeTarget("exists-check");
        WorktreeCreateResult created = backend.create("exists-check", repo.toString(), target).join();

        assertThat(backend.exists(created.getWorktreePath()).join()).isTrue();
    }

    @Test
    void existsReturnsFalseForMissingWorktree() {
        GitBackend backend = new GitBackend(new WorktreeConfig());
        assertThat(backend.exists(tempDir.resolve("nope").toString()).join()).isFalse();
    }

    @Test
    void createBackendReturnsGitBackendByDefault() {
        WorktreeBackend backend = WorktreeBackendRegistry.createBackend("git");
        assertThat(backend).isInstanceOf(GitBackend.class);
    }

    @Test
    void createBackendRejectsUnknownBackendNames() {
        assertThatThrownBy(() -> WorktreeBackendRegistry.createBackend("nonexistent-backend"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown worktree backend");
    }

    @Test
    void registerCustomBackendWorks() {
        String name = "fake-" + UUID.randomUUID();
        try {
            WorktreeBackendRegistry.registerWorktreeBackend(name, FakeBackend::new);
            WorktreeBackend backend = WorktreeBackendRegistry.createBackend(name);
            assertThat(backend).isInstanceOf(FakeBackend.class);
        } finally {
            WorktreeBackendRegistry.unregisterWorktreeBackend(name);
        }
    }

    @Test
    void gitBackendSatisfiesWorktreeBackendContract() {
        assertThat(new GitBackend()).isInstanceOf(WorktreeBackend.class);
    }

    private String worktreeTarget(String slug) {
        return tempDir.resolve("ws").resolve(".worktrees").resolve(slug).toString();
    }

    private Path initRepo(String name) throws IOException, InterruptedException {
        Path repo = Files.createDirectories(tempDir.resolve(name));
        runGit(repo, "init", "-b", "main");
        runGit(repo, "config", "user.name", "Codex");
        runGit(repo, "config", "user.email", "codex@example.com");
        Files.writeString(repo.resolve("README.md"), "# " + name + "\n");
        runGit(repo, "add", "README.md");
        runGit(repo, "commit", "-m", "init");
        runGit(repo, "update-ref", "refs/remotes/origin/main", "HEAD");
        runGit(repo, "symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/main");
        return repo;
    }

    private static void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(buildCommand(args));
        builder.directory(cwd.toFile());
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IOException("git command failed: " + String.join(" ", args) + " :: " + stderr);
        }
    }

    private static List<String> buildCommand(String... args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static final class FakeBackend implements WorktreeBackend {
        private FakeBackend(WorktreeConfig ignored) {
        }

        @Override
        public java.util.concurrent.CompletableFuture<WorktreeCreateResult> create(
                String slug,
                String repoRoot,
                String targetPath
        ) {
            return java.util.concurrent.CompletableFuture.completedFuture(new WorktreeCreateResult(targetPath));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> exists(String worktreePath) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }
}

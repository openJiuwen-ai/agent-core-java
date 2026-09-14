/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.worktree.test_git} in
 * {@code tests/unit_tests/harness/tools/worktree/test_git.py}.</p>
 */
class GitPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void gitErrorMessageFormat() {
        Git.GitError error = new Git.GitError(List.of("rev-parse", "HEAD"), 128, "fatal: bad ref");

        assertThat(error.getMessage()).contains("rev-parse", "128", "fatal: bad ref");
        assertThat(error.getCommand()).containsExactly("rev-parse", "HEAD");
        assertThat(error.getReturncode()).isEqualTo(128);
        assertThat(error.getStderr()).isEqualTo("fatal: bad ref");
    }

    @Test
    void gitErrorIsException() {
        Git.GitError error = new Git.GitError(List.of("status"), 1, "");

        assertThat(error).isInstanceOf(Exception.class);
    }

    @Test
    void gitResultOkTrue() {
        Git.GitResult result = new Git.GitResult(0, "output", "");

        assertThat(result.ok()).isTrue();
    }

    @Test
    void gitResultOkFalse() {
        Git.GitResult result = new Git.GitResult(1, "", "error");

        assertThat(result.ok()).isFalse();
    }

    @Test
    void gitResultOkNonzero() {
        Git.GitResult result = new Git.GitResult(128, "", "fatal");

        assertThat(result.ok()).isFalse();
    }

    @Test
    void gitResultIsImmutableRecord() throws NoSuchFieldException {
        Git.GitResult result = new Git.GitResult(0, "", "");

        assertThat(result.getClass().isRecord()).isTrue();
        assertThat(Modifier.isFinal(result.getClass().getDeclaredField("returncode").getModifiers())).isTrue();
    }

    @Test
    void gitEnvContainsTerminalPrompt() {
        Map<String, String> env = Git.gitEnv();

        assertThat(env).containsEntry("GIT_TERMINAL_PROMPT", "0");
    }

    @Test
    void gitEnvContainsAskpass() {
        Map<String, String> env = Git.gitEnv();

        assertThat(env).containsEntry("GIT_ASKPASS", "");
    }

    @Test
    void gitEnvInheritsEnvironmentPath() {
        Map<String, String> env = Git.gitEnv();
        String expectedPath = System.getenv().getOrDefault("PATH", System.getenv("Path"));

        assertThat(env).containsKey("PATH");
        assertThat(env.get("PATH")).isEqualTo(expectedPath);
    }

    @Disabled("Python baseline failed: tests.unit_tests.harness.tools.worktree.test_git."
            + "TestFindGitRoot::test_in_git_repo; latest-summary.json records a Windows path separator mismatch "
            + "between Git's slash path and tmp_git_repo's backslash path.")
    @Test
    void findGitRootInGitRepo() throws Exception {
        Path repo = createTempGitRepo();

        assertThat(Git.findGitRoot(repo.toString()).join()).isEqualTo(repo.toString());
    }

    @Test
    void findGitRootNotInGitRepo() throws IOException {
        Path notRepo = Files.createDirectories(tempDir.resolve("not-repo"));

        assertThat(Git.findGitRoot(notRepo.toString()).join()).isNull();
    }

    @Test
    void getCurrentBranchReturnsBranch() throws Exception {
        Path repo = createTempGitRepo();

        String branch = Git.getCurrentBranch(repo.toString()).join();

        assertThat(branch).isNotNull().isNotEmpty();
    }

    @Test
    void revParseResolvesHead() throws Exception {
        Path repo = createTempGitRepo();

        String sha = Git.revParse("HEAD", repo.toString()).join();

        assertThat(sha).isNotNull().hasSize(40);
        assertThat(sha).matches("[0-9a-f]{40}");
    }

    @Test
    void revParseInvalidRefReturnsNull() throws Exception {
        Path repo = createTempGitRepo();

        String sha = Git.revParse("nonexistent-ref-xyz", repo.toString()).join();

        assertThat(sha).isNull();
    }

    @Test
    void readWorktreeHeadShaNotAWorktreeReturnsNull() throws Exception {
        Path repo = createTempGitRepo();

        assertThat(Git.readWorktreeHeadSha(repo.toString()).join()).isNull();
    }

    @Test
    void readWorktreeHeadShaNonexistentPathReturnsNull() {
        Path missing = tempDir.resolve("nonexistent");

        assertThat(Git.readWorktreeHeadSha(missing.toString()).join()).isNull();
    }

    @Test
    void statusPorcelainCleanRepoReturnsEmptyList() throws Exception {
        Path repo = createTempGitRepo();

        assertThat(Git.statusPorcelain(repo.toString()).join()).isEmpty();
    }

    @Test
    void statusPorcelainWithChangesReturnsNewFile() throws Exception {
        Path repo = createTempGitRepo();
        Files.writeString(repo.resolve("new.txt"), "hello", StandardCharsets.UTF_8);

        List<String> lines = Git.statusPorcelain(repo.toString()).join();

        assertThat(lines).isNotEmpty();
        assertThat(lines).anyMatch(line -> line.contains("new.txt"));
    }

    @Test
    void countCommitsSinceZeroCommits() throws Exception {
        Path repo = createTempGitRepo();
        String head = Git.revParse("HEAD", repo.toString()).join();

        Integer count = Git.countCommitsSince(head, repo.toString()).join();

        assertThat(count).isEqualTo(0);
    }

    @Test
    void countCommitsSinceWithCommits() throws Exception {
        Path repo = createTempGitRepo();
        String headBefore = Git.revParse("HEAD", repo.toString()).join();
        Files.writeString(repo.resolve("extra.txt"), "content", StandardCharsets.UTF_8);
        runCommand(repo, "git", "add", ".");
        runCommand(repo, "git", "commit", "-m", "second");

        Integer count = Git.countCommitsSince(headBefore, repo.toString()).join();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countCommitsSinceInvalidBaseReturnsNull() throws Exception {
        Path repo = createTempGitRepo();

        Integer count = Git.countCommitsSince("0".repeat(40), repo.toString()).join();

        assertThat(count).isNull();
    }

    private Path createTempGitRepo() throws IOException, InterruptedException {
        Path repo = Files.createDirectories(tempDir.resolve("test_repo_" + System.nanoTime()));
        runCommand(repo, "git", "init");
        runCommand(repo, "git", "config", "user.email", "test@test.com");
        runCommand(repo, "git", "config", "user.name", "Test");
        Files.writeString(repo.resolve("README.md"), "# Test Repo", StandardCharsets.UTF_8);
        runCommand(repo, "git", "add", ".");
        runCommand(repo, "git", "commit", "-m", "Initial commit");
        return repo;
    }

    private static void runCommand(Path cwd, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", "");
        Process process = builder.start();
        String stdout;
        String stderr;
        try (var stdoutStream = process.getInputStream(); var stderrStream = process.getErrorStream()) {
            stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as("command %s stdout=%s stderr=%s", List.of(command), stdout, stderr)
                .isEqualTo(0);
    }
}

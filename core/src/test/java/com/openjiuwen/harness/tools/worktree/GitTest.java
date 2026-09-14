/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.worktree.test_git} in
 * {@code tests/unit_tests/harness/tools/worktree/test_git.py}.</p>
 */
class GitTest {

    @TempDir
    Path tempDir;

    @Test
    void gitEnvSuppressesInteractivePrompts() {
        Map<String, String> env = Git.gitEnv();

        assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
        assertEquals("", env.get("GIT_ASKPASS"));
    }

    @Test
    void gitResultOkMatchesReturnCode() {
        assertTrue(new Git.GitResult(0, "out", "").ok());
        assertFalse(new Git.GitResult(1, "", "err").ok());
    }

    @Test
    void gitResultOkIsFalseForFatalReturnCode() {
        assertFalse(new Git.GitResult(128, "", "fatal").ok());
    }

    @Test
    void gitResultIsImmutableRecord() {
        assertTrue(Git.GitResult.class.isRecord());
        for (Field field : Git.GitResult.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
            }
        }
    }

    @Test
    void gitEnvInheritsPath() {
        Map<String, String> env = Git.gitEnv();
        String expectedPath = System.getenv().containsKey("PATH") ? System.getenv("PATH") : System.getenv("Path");

        assertNotNull(expectedPath);
        assertEquals(expectedPath, env.get("PATH"));
    }

    @Disabled("Python baseline failed: tests.unit_tests.harness.tools.worktree.test_git::"
            + "TestFindGitRoot::test_in_git_repo; javaify-project/tests/python-baseline/"
            + "pytest-20260605-133148.log records a Windows path separator AssertionError "
            + "comparing C:\\... with C:/....")
    @Test
    void findGitRootInGitRepoDisabledBecausePythonBaselineFails() {
        assertTrue(true);
    }

    @Test
    void findGitRootReturnsNullOutsideRepo() throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve("not-repo"));

        assertNull(Git.findGitRoot(directory.toString()).join());
    }

    @Test
    void getCurrentBranchReturnsBranchName() throws Exception {
        Path repo = createGitRepo();

        String branch = Git.getCurrentBranch(repo.toString()).join();

        assertNotNull(branch);
        assertFalse(branch.isBlank());
    }

    @Test
    void revParseResolvesHeadSha() throws Exception {
        Path repo = createGitRepo();

        String sha = Git.revParse("HEAD", repo.toString()).join();

        assertNotNull(sha);
        assertEquals(40, sha.length());
        assertTrue(sha.chars().allMatch(ch -> Character.digit(ch, 16) >= 0));
    }

    @Test
    void revParseReturnsNullForInvalidRef() throws Exception {
        Path repo = createGitRepo();

        assertNull(Git.revParse("nonexistent-ref-xyz", repo.toString()).join());
    }

    @Test
    void readWorktreeHeadShaReturnsNullForRegularRepo() throws Exception {
        Path repo = createGitRepo();

        assertNull(Git.readWorktreeHeadSha(repo.toString()).join());
    }

    @Test
    void statusPorcelainReturnsEmptyForCleanRepo() throws Exception {
        Path repo = createGitRepo();

        assertEquals(List.of(), Git.statusPorcelain(repo.toString()).join());
    }

    @Test
    void statusPorcelainReturnsChangedFiles() throws Exception {
        Path repo = createGitRepo();
        Files.writeString(repo.resolve("new.txt"), "hello");

        List<String> lines = Git.statusPorcelain(repo.toString()).join();

        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().anyMatch(line -> line.contains("new.txt")));
    }

    @Test
    void countCommitsSinceReturnsZeroForHead() throws Exception {
        Path repo = createGitRepo();
        String head = Git.revParse("HEAD", repo.toString()).join();

        assertEquals(0, Git.countCommitsSince(head, repo.toString()).join());
    }

    @Test
    void countCommitsSinceReturnsOneAfterNewCommit() throws Exception {
        Path repo = createGitRepo();
        String head = Git.revParse("HEAD", repo.toString()).join();

        writeAndCommit(repo, "extra.txt", "content", "second");

        assertEquals(1, Git.countCommitsSince(head, repo.toString()).join());
    }

    @Test
    void countCommitsSinceReturnsNullForInvalidBase() throws Exception {
        Path repo = createGitRepo();

        assertNull(Git.countCommitsSince("0".repeat(40), repo.toString()).join());
    }

    @Test
    void readWorktreeHeadShaReadsDetachedHead() throws IOException {
        Path worktree = Files.createDirectories(tempDir.resolve("wt"));
        Path gitDir = Files.createDirectories(tempDir.resolve("gitdir"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir);
        String sha = "1234567890123456789012345678901234567890";
        Files.writeString(gitDir.resolve("HEAD"), sha);

        assertEquals(sha, Git.readWorktreeHeadSha(worktree.toString()).join());
    }

    @Test
    void readWorktreeHeadShaFallsBackToCommondirForBranchRef() throws IOException {
        Path worktree = Files.createDirectories(tempDir.resolve("wt"));
        Path gitDir = Files.createDirectories(tempDir.resolve("gitdir"));
        Path commonGit = Files.createDirectories(tempDir.resolve("common").resolve(".git").resolve("refs").resolve("heads"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main");
        Files.writeString(gitDir.resolve("commondir"), "../common/.git");
        String sha = "abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        Files.writeString(commonGit.resolve("main"), sha);

        assertEquals(sha, Git.readWorktreeHeadSha(worktree.toString()).join());
    }

    @Test
    void gitErrorRetainsCommandAndStderr() {
        Git.GitError error = new Git.GitError(List.of("rev-parse", "HEAD"), 128, "fatal: bad ref");

        assertEquals(128, error.getReturncode());
        assertEquals("fatal: bad ref", error.getStderr());
        assertEquals(List.of("rev-parse", "HEAD"), error.getCommand());
        assertTrue(error.getMessage().contains("rev-parse"));
        assertTrue(error.getMessage().contains("128"));
        assertTrue(error.getMessage().contains("fatal: bad ref"));
    }

    @Test
    void gitErrorIsRuntimeException() {
        Git.GitError error = new Git.GitError(List.of("status"), 1, "");

        assertTrue(error instanceof RuntimeException);
    }

    @Test
    void readWorktreeHeadShaReturnsNullForMissingGitFile() {
        assertNull(Git.readWorktreeHeadSha(tempDir.resolve("missing").toString()).join());
    }

    private Path createGitRepo() throws Exception {
        Path repo = Files.createDirectories(tempDir.resolve("repo-" + System.nanoTime()));
        git(repo, "init");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "config", "user.name", "Test User");
        git(repo, "config", "commit.gpgsign", "false");
        writeAndCommit(repo, "README.md", "initial", "initial");
        return repo;
    }

    private void writeAndCommit(Path repo, String fileName, String content, String message) throws Exception {
        Files.writeString(repo.resolve(fileName), content);
        git(repo, "add", fileName);
        git(repo, "commit", "-m", message);
    }

    private String git(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", "");

        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("git command failed " + command + " rc=" + exitCode + " stderr=" + stderr);
        }
        return stdout.strip();
    }
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Git.GitError error = new Git.GitError(List.of("status", "--porcelain"), 2, "boom");

        assertEquals(2, error.getReturncode());
        assertEquals("boom", error.getStderr());
        assertEquals(List.of("status", "--porcelain"), error.getCommand());
        assertTrue(error.getMessage().contains("git status failed"));
    }

    @Test
    void readWorktreeHeadShaReturnsNullForMissingGitFile() {
        assertNull(Git.readWorktreeHeadSha(tempDir.resolve("missing").toString()).join());
    }
}

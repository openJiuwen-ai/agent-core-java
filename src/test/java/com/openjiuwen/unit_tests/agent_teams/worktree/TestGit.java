/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.Git;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for worktree git module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_git}.
 */
@ExtendWith(MockitoExtension.class)
class TestGit {

    @Nested
    class TestGitError {

        @Test
        @Tag("level0")
        void testMessageFormat() {
            Git.GitError err = new Git.GitError(List.of("rev-parse", "HEAD"), 128, "fatal: bad ref");
            assertTrue(err.getMessage().contains("rev-parse"));
            assertTrue(err.getMessage().contains("128"));
            assertTrue(err.getMessage().contains("fatal: bad ref"));
            assertEquals(List.of("rev-parse", "HEAD"), err.getCommand());
            assertEquals(128, err.getReturncode());
            assertEquals("fatal: bad ref", err.getStderr());
        }

        @Test
        @Tag("level0")
        void testIsException() {
            Git.GitError err = new Git.GitError(List.of("status"), 1, "");
            assertTrue(err instanceof Exception);
        }
    }

    @Nested
    class TestGitResult {

        @Test
        @Tag("level0")
        void testOkTrue() {
            Git.GitResult r = new Git.GitResult(0, "output", "");
            assertTrue(r.ok());
            assertTrue(r.isOk());
        }

        @Test
        @Tag("level0")
        void testOkFalse() {
            Git.GitResult r = new Git.GitResult(1, "", "error");
            assertFalse(r.ok());
            assertFalse(r.isOk());
        }

        @Test
        @Tag("level0")
        void testOkNonzero() {
            Git.GitResult r = new Git.GitResult(128, "", "fatal");
            assertFalse(r.ok());
        }

        @Test
        @Tag("level0")
        void testFrozen() throws Exception {
            Git.GitResult r = new Git.GitResult(0, "", "");
            assertTrue(Git.GitResult.class.isRecord());
            assertEquals(0, r.returncode());
            assertTrue(Modifier.isFinal(Git.GitResult.class.getDeclaredField("returncode").getModifiers()));
        }
    }

    @Nested
    class TestGitEnv {

        @Test
        @Tag("level0")
        void testContainsTerminalPrompt() {
            Map<String, String> env = Git.gitEnv();
            assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
        }

        @Test
        @Tag("level1")
        void testContainsAskpass() {
            Map<String, String> env = Git.gitEnv();
            assertEquals("", env.get("GIT_ASKPASS"));
        }

        @Test
        @Tag("level1")
        void testInheritsEnvironment() {
            Map<String, String> env = Git.gitEnv();
            assertTrue(env.containsKey("PATH"));
            assertEquals(System.getenv().getOrDefault("PATH", System.getenv("Path")), env.get("PATH"));
        }
    }

    @Nested
    class TestFindGitRoot {

        @Test
        @Tag("level1")
        void testInGitRepo(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            String root = Git.findGitRoot(repo.toString()).get();

            assertNotNull(root);
            assertEquals(repo.toRealPath(), Path.of(root).toRealPath());
        }

        @Test
        @Tag("level1")
        void testNotInGitRepo(@TempDir Path tempDir) throws Exception {
            String root = Git.findGitRoot(tempDir.toString()).get();

            assertNull(root);
        }
    }

    @Nested
    class TestGetCurrentBranch {

        @Test
        @Tag("level1")
        void testReturnsBranch(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            String branch = Git.getCurrentBranch(repo.toString()).get();

            assertNotNull(branch);
            assertFalse(branch.isEmpty());
        }
    }

    @Nested
    class TestRevParse {

        @Test
        @Tag("level1")
        void testResolveHead(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            String sha = Git.revParse("HEAD", repo.toString()).get();

            assertNotNull(sha);
            assertEquals(40, sha.length());
            assertTrue(sha.chars().allMatch(c -> "0123456789abcdef".indexOf(c) >= 0));
        }

        @Test
        @Tag("level1")
        void testInvalidRef(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            String sha = Git.revParse("nonexistent-ref-xyz", repo.toString()).get();

            assertNull(sha);
        }
    }

    @Nested
    class TestReadWorktreeHeadSha {

        @Test
        @Tag("level1")
        void testNotAWorktree(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            String result = Git.readWorktreeHeadSha(repo.toString()).get();

            assertNull(result);
        }

        @Test
        @Tag("level1")
        void testNonexistentPath(@TempDir Path tempDir) throws Exception {
            String result = Git.readWorktreeHeadSha(tempDir.resolve("nonexistent").toString()).get();

            assertNull(result);
        }
    }

    @Nested
    class TestStatusPorcelain {

        @Test
        @Tag("level1")
        void testCleanRepo(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            List<String> lines = Git.statusPorcelain(repo.toString()).get();

            assertEquals(List.of(), lines);
        }

        @Test
        @Tag("level1")
        void testWithChanges(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);
            Path newFile = repo.resolve("new.txt");
            Files.writeString(newFile, "hello");

            List<String> lines = Git.statusPorcelain(repo.toString()).get();

            assertFalse(lines.isEmpty());
            assertTrue(lines.stream().anyMatch(line -> line.contains("new.txt")));
        }
    }

    @Nested
    class TestCountCommitsSince {

        @Test
        @Tag("level1")
        void testZeroCommits(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);
            String head = Git.revParse("HEAD", repo.toString()).get();

            Integer count = Git.countCommitsSince(head, repo.toString()).get();

            assertEquals(0, count);
        }

        @Test
        @Tag("level1")
        void testWithCommits(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);
            String headBefore = Git.revParse("HEAD", repo.toString()).get();
            Files.writeString(repo.resolve("extra.txt"), "content");
            Git.runGit(List.of("add", "."), repo.toString(), true).get();
            Git.runGit(List.of("commit", "-m", "second"), repo.toString(), true).get();

            Integer count = Git.countCommitsSince(headBefore, repo.toString()).get();

            assertEquals(1, count);
        }

        @Test
        @Tag("level1")
        void testInvalidBase(@TempDir Path tempDir) throws Exception {
            Path repo = WorktreeTestFixture.tmpGitRepo(tempDir);

            Integer count = Git.countCommitsSince("0".repeat(40), repo.toString()).get();

            assertNull(count);
        }
    }
}

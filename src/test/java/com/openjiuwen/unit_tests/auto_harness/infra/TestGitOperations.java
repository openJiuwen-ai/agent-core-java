/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.GitOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for auto-harness git auth helpers and operations.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_git_operations}.</p>
 */
@DisplayName("Git Operations Tests")
class TestGitOperations {

    @Nested
    @DisplayName("Git Auth Env Tests")
    class TestBuildGitAuthEnv {

        @Test
        @DisplayName("without credentials only disables prompts")
        void testWithoutCredentialsOnlyDisablesPrompts() {
            Map<String, String> env = GitOperations.buildGitAuthEnv("", "");

            assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
            assertEquals("never", env.get("GCM_INTERACTIVE"));
            assertFalse(env.containsKey("GIT_CONFIG_COUNT"));
        }

        @Test
        @DisplayName("with credentials injects gitcode header")
        void testWithCredentialsInjectsGitcodeHeader() {
            Map<String, String> env = GitOperations.buildGitAuthEnv("bot-user", "secret-token");
            String expected = Base64.getEncoder().encodeToString(
                    "bot-user:secret-token".getBytes(StandardCharsets.UTF_8));

            assertEquals("3", env.get("GIT_CONFIG_COUNT"));
            assertEquals("http.https://gitcode.com/.extraheader", env.get("GIT_CONFIG_KEY_2"));
            assertEquals("AUTHORIZATION: basic " + expected, env.get("GIT_CONFIG_VALUE_2"));
        }
    }

    @Nested
    @DisplayName("GitOperations Tests")
    class TestGitOperationsClass {

        @Test
        @DisplayName("git helper preserves leading space in stdout")
        void testGitHelperPreservesLeadingSpaceInStdout() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, " M openjiuwen/auto_harness/schema.py\n");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor);

            GitOperations.GitResult result = git.git("status", "--porcelain");

            assertEquals(0, result.returnCode());
            assertEquals(" M openjiuwen/auto_harness/schema.py", result.output());
        }

        @Test
        @DisplayName("push uses task scoped auth env")
        void testPushUsesTaskScopedAuthEnv() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, "ok");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "fork", "develop", "", "openJiuwen", "agent-core",
                    "bot-user", "secret-token", "", "", executor);

            Map<String, Object> result = git.push("feature-branch");

            assertEquals(true, result.get("success"));
            Map<String, String> env = executor.envs.get(0);
            assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
            assertEquals("http.https://gitcode.com/.extraheader", env.get("GIT_CONFIG_KEY_2"));
        }

        @Test
        @DisplayName("collect status splits tracked and untracked files")
        void testCollectStatusSplitsTrackedAndUntrackedFiles() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, """
                     M openjiuwen/auto_harness/schema.py
                    ?? tests/unit_tests/auto_harness/test_schema.py
                    R  old.py -> new.py""");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor);

            Map<String, List<String>> result = git.collectStatus();

            assertEquals(List.of(
                    "openjiuwen/auto_harness/schema.py",
                    "tests/unit_tests/auto_harness/test_schema.py",
                    "new.py"), result.get("dirty_files"));
            assertEquals(List.of(
                    "openjiuwen/auto_harness/schema.py",
                    "new.py"), result.get("tracked_modified_files"));
            assertEquals(List.of("tests/unit_tests/auto_harness/test_schema.py"), result.get("untracked_files"));
            assertEquals(List.of("new.py"), result.get("renamed_files"));
        }

        @Test
        @DisplayName("status porcelain returns raw output")
        void testStatusPorcelainReturnsRawOutput() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, """
                     M openjiuwen/auto_harness/schema.py
                    ?? tests/unit_tests/auto_harness/test_schema.py""");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor);

            String result = git.statusPorcelain();

            assertEquals("""
                     M openjiuwen/auto_harness/schema.py
                    ?? tests/unit_tests/auto_harness/test_schema.py""", result);
            assertEquals(List.of("git", "status", "--porcelain", "--untracked-files=all"), executor.commands.get(0));
        }

        @Test
        @DisplayName("show last commit stat returns compact summary")
        void testShowLastCommitStatReturnsCompactSummary() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, """
                    commit abc123
                    Author: auto-harness

                     1 file changed, 2 insertions(+)""");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor);

            String result = git.showLastCommitStat();

            assertEquals("""
                    commit abc123
                    Author: auto-harness

                     1 file changed, 2 insertions(+)""", result);
            assertEquals(List.of("git", "show", "--stat", "--format=fuller", "-1"), executor.commands.get(0));
        }

        @Test
        @DisplayName("diff name only returns normalized unique paths")
        void testDiffNameOnlyReturnsNormalizedUniquePaths() throws Exception {
            FakeExecutor executor = new FakeExecutor();
            executor.enqueue(0, """
                    openjiuwen\\core\\foo.py
                    tests/unit_tests/test_foo.py
                    openjiuwen\\core\\foo.py""");
            GitOperations git = new GitOperations(
                    "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor);

            List<String> result = git.diffNameOnly("HEAD");

            assertEquals(List.of(
                    "openjiuwen/core/foo.py",
                    "tests/unit_tests/test_foo.py"), result);
            assertEquals(List.of("git", "diff", "--name-only", "HEAD"), executor.commands.get(0));
        }
    }

    private static final class FakeExecutor implements GitOperations.CommandExecutor {
        private final Queue<GitOperations.CommandResult> queued = new ArrayDeque<>();
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Map<String, String>> envs = new ArrayList<>();

        void enqueue(int code, String output) {
            queued.add(new GitOperations.CommandResult(code, output));
        }

        @Override
        public GitOperations.CommandResult execute(List<String> command, String cwd, Map<String, String> env) {
            commands.add(List.copyOf(command));
            envs.add(Map.copyOf(env));
            if (queued.isEmpty()) {
                return new GitOperations.CommandResult(0, "");
            }
            return queued.remove();
        }
    }
}

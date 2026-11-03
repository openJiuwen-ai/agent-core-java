/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestBuildGitAuthEnv} and {@code TestGitOperations} in
 * {@code tests/unit_tests/auto_harness/infra/test_git_operations.py}.
 */
class GitOperationsPythonParityTest {

    @Test
    void withoutCredentialsOnlyDisablesPrompts() {
        Map<String, String> env = GitAuth.buildGitAuthEnv("", "", Map.of());

        assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
        assertEquals("never", env.get("GCM_INTERACTIVE"));
        assertFalse(env.containsKey("GIT_CONFIG_COUNT"));
    }

    @Test
    void withCredentialsInjectsGitcodeHeader() {
        Map<String, String> env = GitAuth.buildGitAuthEnv("bot-user", "secret-token", Map.of());
        String expected = Base64.getEncoder()
                .encodeToString("bot-user:secret-token".getBytes(StandardCharsets.US_ASCII));

        assertEquals("3", env.get("GIT_CONFIG_COUNT"));
        assertEquals("http.https://gitcode.com/.extraheader", env.get("GIT_CONFIG_KEY_2"));
        assertEquals("AUTHORIZATION: basic " + expected, env.get("GIT_CONFIG_VALUE_2"));
    }

    @Test
    void gitHelperPreservesLeadingSpaceInStdout() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(0, " M openjiuwen/auto_harness/schema.py\n")
        );
        GitOperations git = new GitOperations(
                "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        GitOperations.GitResult result = git.git("status", "--porcelain");

        assertEquals(0, result.returnCode());
        assertEquals(" M openjiuwen/auto_harness/schema.py", result.output());
    }

    @Test
    void pushUsesTaskScopedAuthEnv() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(new GitOperations.CommandResult(0, "ok"));
        GitOperations git = new GitOperations(
                "/tmp/worktree", "fork", "develop", "", "openJiuwen", "agent-core",
                "bot-user", "secret-token", "", "", executor
        );

        Map<String, Object> result = git.push("feature-branch");

        assertTrue((Boolean) result.get("success"));
        assertEquals("0", executor.lastEnv.get("GIT_TERMINAL_PROMPT"));
        assertEquals("http.https://gitcode.com/.extraheader", executor.lastEnv.get("GIT_CONFIG_KEY_2"));
    }

    @Test
    void collectStatusSplitsTrackedAndUntrackedFiles() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(
                        0,
                        " M openjiuwen/auto_harness/schema.py\n"
                                + "?? tests/unit_tests/auto_harness/test_schema.py\n"
                                + "R  old.py -> new.py"
                )
        );
        GitOperations git = new GitOperations(
                "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        Map<String, List<String>> result = git.collectStatus();

        assertIterableEquals(
                List.of("openjiuwen/auto_harness/schema.py", "tests/unit_tests/auto_harness/test_schema.py", "new.py"),
                result.get("dirty_files")
        );
        assertIterableEquals(List.of("openjiuwen/auto_harness/schema.py", "new.py"),
                result.get("tracked_modified_files"));
        assertIterableEquals(List.of("tests/unit_tests/auto_harness/test_schema.py"), result.get("untracked_files"));
        assertIterableEquals(List.of("new.py"), result.get("renamed_files"));
    }

    @Test
    void statusPorcelainReturnsRawOutput() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(
                        0,
                        " M openjiuwen/auto_harness/schema.py\n"
                                + "?? tests/unit_tests/auto_harness/test_schema.py"
                )
        );
        GitOperations git = new GitOperations(
                "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        String result = git.statusPorcelain();

        assertEquals(
                " M openjiuwen/auto_harness/schema.py\n?? tests/unit_tests/auto_harness/test_schema.py",
                result
        );
        assertIterableEquals(List.of("git", "status", "--porcelain", "--untracked-files=all"), executor.lastCommand);
    }

    @Test
    void showLastCommitStatReturnsCompactSummary() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(
                        0,
                        "commit abc123\nAuthor: auto-harness\n\n 1 file changed, 2 insertions(+)"
                )
        );
        GitOperations git = new GitOperations(
                "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        String result = git.showLastCommitStat();

        assertEquals("commit abc123\nAuthor: auto-harness\n\n 1 file changed, 2 insertions(+)", result);
        assertIterableEquals(List.of("git", "show", "--stat", "--format=fuller", "-1"), executor.lastCommand);
    }

    @Test
    void diffNameOnlyReturnsNormalizedUniquePaths() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(
                        0,
                        "openjiuwen\\core\\foo.py\n"
                                + "tests/unit_tests/test_foo.py\n"
                                + "openjiuwen\\core\\foo.py"
                )
        );
        GitOperations git = new GitOperations(
                "/tmp/worktree", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        List<String> result = git.diffNameOnly("HEAD");

        assertIterableEquals(List.of("openjiuwen/core/foo.py", "tests/unit_tests/test_foo.py"), result);
        assertIterableEquals(List.of("git", "diff", "--name-only", "HEAD"), executor.lastCommand);
    }

    private static final class RecordingExecutor implements GitOperations.CommandExecutor {

        private final Deque<GitOperations.CommandResult> results = new ArrayDeque<>();
        private List<String> lastCommand = List.of();
        private Map<String, String> lastEnv = Map.of();

        private RecordingExecutor(GitOperations.CommandResult... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        public GitOperations.CommandResult execute(List<String> command, String cwd, Map<String, String> env)
                throws IOException {
            if (results.isEmpty()) {
                throw new IOException("No fake result queued.");
            }
            lastCommand = List.copyOf(command);
            lastEnv = Map.copyOf(env);
            return results.removeFirst();
        }
    }
}

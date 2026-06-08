/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's focused git-operations behaviors in
 * {@code openjiuwen/auto_harness/infra/git_operations.py}.
 */
class GitOperationsTest {

    @Test
    void collectStatusParsesDirtyRenamedAndUntrackedFiles() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(
                        0,
                        " M tracked.txt\nR  old_name.py -> folder\\new_name.py\n?? scratch\\note.txt"
                )
        );
        GitOperations operations = new GitOperations(
                "D:/repo", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        Map<String, List<String>> status = operations.collectStatus();

        assertIterableEquals(List.of("tracked.txt", "folder/new_name.py", "scratch/note.txt"), status.get("dirty_files"));
        assertIterableEquals(List.of("tracked.txt", "folder/new_name.py"), status.get("tracked_modified_files"));
        assertIterableEquals(List.of("scratch/note.txt"), status.get("untracked_files"));
        assertIterableEquals(List.of("folder/new_name.py"), status.get("renamed_files"));
    }

    @Test
    void diffNameOnlyNormalizesAndDeduplicatesPaths() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(0, "one.py\nfolder\\two.py\nfolder/two.py\n")
        );
        GitOperations operations = new GitOperations(
                "D:/repo", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        List<String> diff = operations.diffNameOnly("HEAD~1");

        assertIterableEquals(List.of("one.py", "folder/two.py"), diff);
    }

    @Test
    void createBranchUsesGitAuthEnvironmentAndReturnsSuccessEnvelope() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new GitOperations.CommandResult(0, "Switched to a new branch 'feature/demo'")
        );
        GitOperations operations = new GitOperations(
                "D:/repo", "origin", "develop", "alice", "openJiuwen", "agent-core",
                "alice", "secret", "", "", executor
        );

        Map<String, Object> result = operations.createBranch("feature/demo");

        assertEquals(List.of("git", "checkout", "-b", "feature/demo"), executor.lastCommand);
        assertEquals("D:/repo", executor.lastCwd);
        assertEquals("0", executor.lastEnv.get("GIT_TERMINAL_PROMPT"));
        assertEquals("never", executor.lastEnv.get("GCM_INTERACTIVE"));
        assertEquals("3", executor.lastEnv.get("GIT_CONFIG_COUNT"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("feature/demo", result.get("branch"));
        assertTrue(String.valueOf(result.get("output")).contains("feature/demo"));
    }

    @Test
    void discardWorktreeChangesReflectsGitReturnCode() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(new GitOperations.CommandResult(1, "checkout failed"));
        GitOperations operations = new GitOperations(
                "D:/repo", "", "develop", "", "openJiuwen", "agent-core", "", "", "", "", executor
        );

        assertFalse(operations.discardWorktreeChanges());
        assertEquals(List.of("git", "checkout", "."), executor.lastCommand);
    }

    private static final class RecordingExecutor implements GitOperations.CommandExecutor {

        private final Deque<GitOperations.CommandResult> results = new ArrayDeque<>();
        private List<String> lastCommand = List.of();
        private String lastCwd = "";
        private Map<String, String> lastEnv = Map.of();

        private RecordingExecutor(GitOperations.CommandResult... results) {
            for (GitOperations.CommandResult result : results) {
                this.results.addLast(result);
            }
        }

        @Override
        public GitOperations.CommandResult execute(List<String> command, String cwd, Map<String, String> env)
                throws IOException {
            if (results.isEmpty()) {
                throw new IOException("No fake result queued.");
            }
            lastCommand = List.copyOf(command);
            lastCwd = cwd;
            lastEnv = Map.copyOf(env);
            return results.removeFirst();
        }
    }
}

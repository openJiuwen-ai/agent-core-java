/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.tools.worktree.Git;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.team_workspace.test_manager} in
 * {@code tests/unit_tests/agent_teams/team_workspace/test_manager.py}.</p>
 */
class TeamWorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void mountIntoWorkspaceUsesMountDirectory() throws Exception {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());
        Path workspaceRoot = tempDir.resolve("agent-workspace");
        Files.createDirectories(workspaceRoot);

        manager.mountIntoWorkspace(workspaceRoot.toString());

        String expectedLink = workspaceRoot.resolve(".team").resolve("team-alpha").toString();
        assertEquals(List.of(Map.of("target", manager.getWorkspacePath(), "link", expectedLink)), manager.mountCalls);
    }

    @Test
    void mountIntoWorkspaceReplacesStaleDirectoryAndMergesFiles() throws Exception {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());
        Path workspaceRoot = tempDir.resolve("agent-workspace");
        Path staleMount = workspaceRoot.resolve(".team").resolve("team-alpha");
        Path staleArtifacts = staleMount.resolve("artifacts");
        Path canonicalArtifacts = Path.of(manager.getWorkspacePath()).resolve("artifacts");
        Files.createDirectories(staleArtifacts);
        Files.createDirectories(canonicalArtifacts);
        Files.writeString(staleArtifacts.resolve("only-stale.md"), "from stale");
        Files.writeString(staleArtifacts.resolve("existing.md"), "old");
        Files.writeString(canonicalArtifacts.resolve("existing.md"), "new");

        manager.mountIntoWorkspace(workspaceRoot.toString());

        String expectedLink = workspaceRoot.resolve(".team").resolve("team-alpha").toString();
        assertEquals(List.of(Map.of("target", manager.getWorkspacePath(), "link", expectedLink)), manager.mountCalls);
        assertEquals("from stale", Files.readString(canonicalArtifacts.resolve("only-stale.md")));
        assertEquals("new", Files.readString(canonicalArtifacts.resolve("existing.md")));
        assertFalse(Files.exists(staleMount));
        try (var stream = Files.list(workspaceRoot.resolve(".team"))) {
            assertEquals(1L, stream.filter(path -> path.getFileName().toString().startsWith("team-alpha.stale-")).count());
        }
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.agent_teams.team_workspace.test_manager::"
                    + "test_mount_into_workspace_falls_back_to_junction_on_windows_1314: "
                    + "fake_run() got an unexpected keyword argument 'shell'. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void mountIntoWorkspaceFallsBackToJunctionOnWindows1314() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Test
    void mountIntoWorkspaceReraisesNonPrivilegeMountFailure() throws Exception {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        IOException expected = new IOException("unexpected failure");
        TeamWorkspaceManager manager = new TeamWorkspaceManager(config, tempDir.resolve("shared-workspace").toString(), "team-alpha") {
            @Override
            protected void mountDirectory(String targetPath, String linkPath) throws IOException {
                throw expected;
            }
        };
        Files.createDirectories(Path.of(manager.getWorkspacePath()));
        Path workspaceRoot = tempDir.resolve("agent-workspace");
        Files.createDirectories(workspaceRoot);

        IOException actual = assertThrows(IOException.class, () -> manager.mountIntoWorkspace(workspaceRoot.toString()));

        assertEquals("unexpected failure", actual.getMessage());
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.agent_teams.team_workspace.test_manager::test_mount_worktree_creates_symlink: "
                    + "os.path.islink returned false on the created worktree mount. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void mountWorktreeCreatesSymlink() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.agent_teams.team_workspace.test_manager::"
                    + "test_mount_worktree_replaces_stale_symlink: stale target remained instead of fresh target. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void mountWorktreeReplacesStaleSymlink() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Test
    void mountWorktreeSkipsWhenCollisionIsNotSymlink() throws Exception {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());
        Path target = tempDir.resolve("worktrees").resolve("wt-collide");
        Files.createDirectories(target);
        Path worktreeDir = Path.of(manager.getWorkspacePath()).resolve(".worktree");
        Files.createDirectories(worktreeDir);
        Path realDirectory = worktreeDir.resolve("wt-collide");
        Files.createDirectories(realDirectory);

        manager.mountWorktree("wt-collide", target.toString());

        assertTrue(Files.isDirectory(realDirectory));
        assertFalse(Files.isSymbolicLink(realDirectory));
        assertEquals(List.of(), manager.mountCalls);
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.agent_teams.team_workspace.test_manager::"
                    + "test_unmount_worktree_removes_symlink: os.path.lexists stayed true after unmount. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void unmountWorktreeRemovesSymlink() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Test
    void unmountWorktreeNoopWhenLinkMissing() {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());

        assertDoesNotThrow(() -> manager.unmountWorktree("never-mounted"));
    }

    @Test
    void initializeWithoutVersionControlSkipsGit() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        config.setVersionControl(false);
        RecordingManager manager = makeManager(config);

        manager.initialize().join();

        assertEquals(List.of(), manager.gitCalls);
        assertFalse(Files.isDirectory(Path.of(manager.getWorkspacePath()).resolve(".git")));
        for (String directory : manager.getConfig().getArtifactDirs()) {
            assertTrue(Files.isDirectory(Path.of(manager.getWorkspacePath()).resolve(directory)));
        }
        assertTrue(Files.isDirectory(Path.of(manager.getWorkspacePath()).resolve("skills")));
    }

    @Test
    void initializeWithVersionControlRunsGitInitAndCommit() {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());

        manager.initialize().join();

        List<String> subcommands = manager.gitCalls.stream().map(call -> call.getFirst()).toList();
        assertTrue(subcommands.contains("init"));
        assertTrue(subcommands.contains("commit"));
    }

    @Test
    void autoCommitNoopWhenVersionControlDisabled() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        config.setVersionControl(false);
        RecordingManager manager = makeManager(config);

        String sha = manager.autoCommit("artifacts/code/a.py", "alice").join();

        assertNull(sha);
        assertEquals(List.of(), manager.gitCalls);
    }

    @Test
    void pullPushHistoryNoopWhenVersionControlDisabled() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        config.setVersionControl(false);
        RecordingManager manager = makeManager(config);

        assertFalse(manager.pull().join());
        assertTrue(manager.push().join());
        assertEquals(List.of(), manager.getHistory("artifacts/code/a.py").join());
        assertEquals(List.of(), manager.gitCalls);
    }

    @Test
    void localLocksAreReentrantForSameHolder() {
        RecordingManager manager = makeManager(new TeamWorkspaceConfig());

        assertTrue(manager.acquireLock("docs/a.md", "alice", "Alice").join());
        assertTrue(manager.acquireLock("docs/a.md", "alice", "Alice", 60).join());
        WorkspaceFileLock lock = manager.getLock("docs/a.md");
        assertNotNull(lock);
        assertEquals("alice", lock.getHolderId());
        assertEquals(60, lock.getTimeoutSeconds());
        assertTrue(manager.releaseLock("docs/a.md", "alice").join());
        assertNull(manager.getLock("docs/a.md"));
    }

    private RecordingManager makeManager(TeamWorkspaceConfig config) {
        try {
            Path workspacePath = tempDir.resolve("shared-workspace");
            Files.createDirectories(workspacePath);
            return new RecordingManager(config, workspacePath.toString(), "team-alpha");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static final class RecordingManager extends TeamWorkspaceManager {

        private final List<Map<String, String>> mountCalls = new ArrayList<>();
        private final List<List<String>> gitCalls = new ArrayList<>();

        private RecordingManager(TeamWorkspaceConfig config, String workspacePath, String teamName) {
            super(config, workspacePath, teamName);
        }

        @Override
        protected void mountDirectory(String targetPath, String linkPath) {
            mountCalls.add(Map.of("target", targetPath, "link", linkPath));
        }

        @Override
        protected CompletableFuture<Git.GitResult> runGit(List<String> args, String cwd, boolean check) {
            gitCalls.add(List.copyOf(args));
            return CompletableFuture.completedFuture(new Git.GitResult(0, "", ""));
        }
    }
}

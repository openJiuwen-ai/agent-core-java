package com.openjiuwen.agent_teams.team_workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TeamWorkspaceManager} and workspace models in
 * {@code openjiuwen.agent_teams.team_workspace.manager}, including
 * {@code tests/unit_tests/agent_teams/team_workspace/test_manager.py}.
 */
class WorkspaceManagerTest {

    @Test
    void configDefaultsMatchPythonWorkspaceModel() {
        WorkspaceManager.TeamWorkspaceConfig config = new WorkspaceManager.TeamWorkspaceConfig();

        assertEquals(
            List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"),
            config.getArtifactDirs()
        );
        assertTrue(config.isVersionControl());
        assertEquals(WorkspaceManager.ConflictStrategy.LOCK, config.getConflictStrategy());
    }

    @Test
    void sameHolderAcquireRefreshesExistingLock() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();

        assertTrue(manager.acquireLock("src/main.py", "member-a", "Alice").join());
        long firstAcquiredAt = manager.getLock("src/main.py").getAcquiredAt();

        Thread.sleep(5L);
        assertTrue(manager.acquireLock("src/main.py", "member-a", "Alice").join());

        long refreshedAt = manager.getLock("src/main.py").getAcquiredAt();
        assertTrue(refreshedAt > firstAcquiredAt);
    }

    @Test
    void getLockAndListLocksDropExpiredEntries() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();

        assertTrue(manager.acquireLock("src/main.py", "member-a", "Alice", 0).join());
        Thread.sleep(5L);

        assertNull(manager.getLock("src/main.py"));
        assertTrue(manager.listLocks().isEmpty());
    }

    @Test
    void mountIntoWorkspaceCreatesTeamMountPoint() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();
        Path workspaceRoot = Files.createTempDirectory("agent-workspace");

        manager.mountIntoWorkspace(workspaceRoot.toString());

        Path mount = workspaceRoot.resolve(".team").resolve("team-alpha");
        assertTrue(Files.exists(mount));
    }

    @Test
    void initializeWithoutVersionControlCreatesArtifactDirsAndSkipsGit() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();

        manager.initialize().join();

        Path workspace = Path.of(manager.getWorkspacePath());
        assertFalse(Files.exists(workspace.resolve(".git")));
        for (String dir : manager.getConfig().getArtifactDirs()) {
            assertTrue(Files.isDirectory(workspace.resolve(dir)));
        }
        assertTrue(Files.isDirectory(workspace.resolve("skills")));
    }

    @Test
    void autoCommitNoopsWhenVersionControlDisabled() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();

        manager.autoCommit("artifacts/code/a.py", "alice").join();

        assertFalse(Files.exists(Path.of(manager.getWorkspacePath()).resolve(".git")));
    }

    @Test
    void pullPushAndHistoryNoopWhenVersionControlDisabled() throws Exception {
        WorkspaceManager manager = managerWithPlainWorkspace();

        assertFalse(manager.pull().join());
        assertTrue(manager.push().join());
        assertTrue(manager.getHistory("artifacts/code/a.py").join().isEmpty());
    }

    private static WorkspaceManager managerWithPlainWorkspace() throws Exception {
        WorkspaceManager.TeamWorkspaceConfig config = new WorkspaceManager.TeamWorkspaceConfig();
        config.setVersionControl(false);
        Path workspace = Files.createTempDirectory("team-workspace-manager");
        return new WorkspaceManager(
            config,
            workspace.toString(),
            "team-alpha",
            WorkspaceManager.WorkspaceMode.LOCAL,
            null,
            "leader",
            "leader",
            null
        );
    }
}

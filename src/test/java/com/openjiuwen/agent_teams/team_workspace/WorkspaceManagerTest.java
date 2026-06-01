package com.openjiuwen.agent_teams.team_workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TeamWorkspaceManager} and workspace models in
 * {@code openjiuwen.agent_teams.team_workspace.manager}.
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

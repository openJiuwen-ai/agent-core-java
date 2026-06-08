/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TeamWorkspaceModelsTest {

    @Test
    void workspaceModeEnumValuesMatchPython() {
        assertEquals("local", WorkspaceMode.LOCAL.value());
        assertEquals("distributed", WorkspaceMode.DISTRIBUTED.value());
        assertEquals(2, WorkspaceMode.values().length);
    }

    @Test
    void conflictStrategyEnumValuesMatchPython() {
        assertEquals("lock", ConflictStrategy.LOCK.value());
        assertEquals("merge", ConflictStrategy.MERGE.value());
        assertEquals("last_write_wins", ConflictStrategy.LAST_WRITE_WINS.value());
        assertEquals(3, ConflictStrategy.values().length);
    }

    @Test
    void teamWorkspaceConfigDefaultsMatchPython() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();

        assertFalse(config.isEnabled());
        assertEquals(
                java.util.List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"),
                config.getArtifactDirs()
        );
        assertTrue(config.isVersionControl());
        assertEquals(ConflictStrategy.LOCK, config.getConflictStrategy());
        assertEquals(null, config.getRemoteUrl());
    }

    @Test
    void teamWorkspaceConfigCustomValuesAreRetained() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig(
                true,
                null,
                new java.util.ArrayList<>(java.util.List.of("out/")),
                false,
                ConflictStrategy.MERGE,
                "git@github.com:org/ws.git"
        );

        assertEquals(ConflictStrategy.MERGE, config.getConflictStrategy());
        assertEquals("git@github.com:org/ws.git", config.getRemoteUrl());
    }

    @Test
    void workspaceFileLockDefaultsAndExpiryMatchPython() {
        WorkspaceFileLock lock = new WorkspaceFileLock();
        lock.setFilePath("src/main.py");
        lock.setHolderId("m1");
        lock.setHolderName("Alice");
        lock.setAcquiredAt("2025-01-01T00:00:00+00:00");

        assertEquals("src/main.py", lock.getFilePath());
        assertEquals("m1", lock.getHolderId());
        assertEquals(300, lock.getTimeoutSeconds());
    }

    @Test
    void workspaceFileLockIsExpiredFalseForFreshLock() {
        WorkspaceFileLock lock = new WorkspaceFileLock(
                "f.py",
                "m1",
                "Bob",
                OffsetDateTime.now().toString(),
                600
        );

        assertFalse(lock.isExpired());
    }

    @Test
    void workspaceFileLockIsExpiredTrueForOldLock() {
        WorkspaceFileLock lock = new WorkspaceFileLock(
                "f.py",
                "m1",
                "Bob",
                OffsetDateTime.now().minusSeconds(400).toString(),
                300
        );

        assertTrue(lock.isExpired());
    }

    @Test
    void workspaceFileLockBoundaryPastTimeoutIsExpired() {
        WorkspaceFileLock lock = new WorkspaceFileLock(
                "f.py",
                "m1",
                "Bob",
                OffsetDateTime.now().minusSeconds(301).toString(),
                300
        );

        assertTrue(lock.isExpired());
    }
}

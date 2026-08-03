/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/team_workspace/test_models.py}.
 */
class TeamWorkspaceModelsPythonParityTest {

    @Test
    void workspaceModeEnumValues() {
        assertEquals("local", WorkspaceMode.LOCAL.value());
        assertEquals("distributed", WorkspaceMode.DISTRIBUTED.value());
    }

    @Test
    void workspaceModeAllMembers() {
        assertEquals(2, Set.of(WorkspaceMode.values()).size());
    }

    @Test
    void conflictStrategyEnumValues() {
        assertEquals("lock", ConflictStrategy.LOCK.value());
        assertEquals("merge", ConflictStrategy.MERGE.value());
        assertEquals("last_write_wins", ConflictStrategy.LAST_WRITE_WINS.value());
    }

    @Test
    void conflictStrategyAllMembers() {
        assertEquals(3, Set.of(ConflictStrategy.values()).size());
    }

    @Test
    void teamWorkspaceConfigDefaults() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();

        assertFalse(config.isEnabled());
        assertEquals(List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"),
                config.getArtifactDirs());
        assertTrue(config.isVersionControl());
        assertEquals(ConflictStrategy.LOCK, config.getConflictStrategy());
        assertNull(config.getRemoteUrl());
    }

    @Test
    void teamWorkspaceConfigCustom() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig(
                true,
                null,
                List.of("out/"),
                false,
                ConflictStrategy.MERGE,
                "git@github.com:org/ws.git"
        );

        assertEquals(ConflictStrategy.MERGE, config.getConflictStrategy());
        assertEquals("git@github.com:org/ws.git", config.getRemoteUrl());
    }

    @Test
    void workspaceFileLockFields() {
        WorkspaceFileLock lock = new WorkspaceFileLock(
                "src/main.py",
                "m1",
                "Alice",
                "2025-01-01T00:00:00+00:00",
                300
        );

        assertEquals("src/main.py", lock.getFilePath());
        assertEquals("m1", lock.getHolderId());
        assertEquals(300, lock.getTimeoutSeconds());
    }

    @Test
    void workspaceFileLockIsExpiredFalse() {
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
    void workspaceFileLockIsExpiredTrue() {
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
    void workspaceFileLockIsExpiredBoundary() {
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

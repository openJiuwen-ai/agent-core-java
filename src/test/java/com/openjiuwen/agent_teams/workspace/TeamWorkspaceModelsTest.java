/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_teams.team_workspace.test_models.
 * Tests for team workspace models.
 */
class TeamWorkspaceModelsTest {

    @Test
    void workspaceModeEnumValues() {
        assertEquals("local", WorkspaceMode.LOCAL.getValue());
        assertEquals("distributed", WorkspaceMode.DISTRIBUTED.getValue());
    }

    @Test
    void workspaceModeAllMembers() {
        WorkspaceMode[] values = WorkspaceMode.values();
        assertEquals(2, values.length);
    }

    @Test
    void conflictStrategyEnumValues() {
        assertEquals("lock", ConflictStrategy.LOCK.getValue());
        assertEquals("merge", ConflictStrategy.MERGE.getValue());
        assertEquals("last_write_wins", ConflictStrategy.LAST_WRITE_WINS.getValue());
    }

    @Test
    void conflictStrategyAllMembers() {
        ConflictStrategy[] values = ConflictStrategy.values();
        assertEquals(3, values.length);
    }

    @Test
    void teamWorkspaceConfigDefaults() {
        TeamWorkspaceConfig cfg = new TeamWorkspaceConfig();
        
        assertFalse(cfg.isEnabled());
        assertTrue(cfg.isVersionControl());
        assertEquals(ConflictStrategy.LOCK, cfg.getConflictStrategy());
        assertNull(cfg.getRemoteUrl());
    }

    @Test
    void teamWorkspaceConfigCustom() {
        TeamWorkspaceConfig cfg = TeamWorkspaceConfig.builder()
                .enabled(true)
                .artifactDirs(List.of("out/"))
                .versionControl(false)
                .conflictStrategy(ConflictStrategy.MERGE)
                .remoteUrl("git@github.com:org/ws.git")
                .build();

        assertTrue(cfg.isEnabled());
        assertEquals(ConflictStrategy.MERGE, cfg.getConflictStrategy());
        assertEquals("git@github.com:org/ws.git", cfg.getRemoteUrl());
    }

    @Test
    void workspaceFileLockFields() {
        WorkspaceFileLock lock = WorkspaceFileLock.builder()
                .filePath("src/main.py")
                .holderId("m1")
                .holderName("Alice")
                .acquiredAt("2025-01-01T00:00:00+00:00")
                .build();

        assertEquals("src/main.py", lock.getFilePath());
        assertEquals("m1", lock.getHolderId());
        assertEquals(300, lock.getTimeoutSeconds());
    }
}
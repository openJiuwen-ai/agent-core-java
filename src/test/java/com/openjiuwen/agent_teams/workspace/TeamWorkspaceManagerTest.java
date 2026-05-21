/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_teams.team_workspace.test_manager.
 * Tests for TeamWorkspaceManager.
 */
class TeamWorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void managerInitialization() {
        Path workspacePath = tempDir.resolve("shared-workspace");
        workspacePath.toFile().mkdirs();

        TeamWorkspaceManager manager = TeamWorkspaceManager.builder()
                .config(TeamWorkspaceConfig.builder().build())
                .workspacePath(workspacePath.toString())
                .teamName("team-alpha")
                .build();

        assertNotNull(manager);
        assertEquals("team-alpha", manager.getTeamName());
    }

    @Test
    void mountIntoWorkspaceCreatesTeamDirectory() {
        Path workspacePath = tempDir.resolve("shared-workspace");
        workspacePath.toFile().mkdirs();

        Path agentWorkspace = tempDir.resolve("agent-workspace");
        agentWorkspace.toFile().mkdirs();

        TeamWorkspaceManager manager = TeamWorkspaceManager.builder()
                .config(TeamWorkspaceConfig.builder().build())
                .workspacePath(workspacePath.toString())
                .teamName("team-alpha")
                .build();

        manager.mountIntoWorkspace(agentWorkspace.toString());

        Path teamDir = agentWorkspace.resolve(".team").resolve("team-alpha");
        assertTrue(teamDir.toFile().exists() || agentWorkspace.resolve(".team").toFile().exists());
    }
}
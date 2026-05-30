/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamWorkspaceManager.
 * 
 * <p>Mirrors Python's {@code TeamWorkspaceManager} in
 * {@code openjiuwen.agent_teams.team_workspace.manager}.
 * Ported from Python: agent-core-0.1.12/openjiuwen/agent_teams/team_workspace/manager.py
 * 
 * <p>NOTE: Python has no dedicated test file for TeamWorkspaceManager.
 * Tests are derived from the Java/Python implementation behavior.
 */
@ExtendWith(MockitoExtension.class)
class TeamWorkspaceManagerTest {

    private TeamWorkspaceManager createManager(String workspacePath, String teamName) {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        return new TeamWorkspaceManager(config, workspacePath, teamName);
    }

    private WorkspaceFileLock createLock(String filePath, String holderId, int timeoutSeconds) {
        String acquiredAt = Instant.now().toString();
        return new WorkspaceFileLock(filePath, holderId, holderId + "-name", acquiredAt, timeoutSeconds);
    }

    private WorkspaceFileLock createExpiredLock(String filePath, String holderId, int timeoutSeconds) {
        String acquiredAt = Instant.now().minusSeconds(timeoutSeconds + 1L).toString();
        return new WorkspaceFileLock(filePath, holderId, holderId + "-name", acquiredAt, timeoutSeconds);
    }

    // ========== Constructor tests ==========

    @Test
    @DisplayName("Test constructor creates manager with default mode")
    void testConstructorDefaultMode() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        assertEquals("/tmp/workspace", manager.getWorkspacePath());
        assertEquals("test-team", manager.getTeamName());
        assertEquals(WorkspaceMode.LOCAL, manager.getMode());
    }

    @Test
    @DisplayName("Test constructor with custom mode")
    void testConstructorCustomMode() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        TeamWorkspaceManager manager = new TeamWorkspaceManager(
            config, "/tmp/workspace", "test-team", WorkspaceMode.DISTRIBUTED
        );
        assertEquals(WorkspaceMode.DISTRIBUTED, manager.getMode());
    }

    @Test
    @DisplayName("Test constructor with null config uses default")
    void testConstructorNullConfig() {
        TeamWorkspaceManager manager = new TeamWorkspaceManager(
            null, "/tmp/workspace", "test-team"
        );
        assertNotNull(manager);
        assertEquals("/tmp/workspace", manager.getWorkspacePath());
    }

    @Test
    @DisplayName("Test constructor with null mode uses LOCAL")
    void testConstructorNullMode() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        TeamWorkspaceManager manager = new TeamWorkspaceManager(
            config, "/tmp/workspace", "test-team", null
        );
        assertEquals(WorkspaceMode.LOCAL, manager.getMode());
    }

    // ========== Lock tests ==========

    @Test
    @DisplayName("Test acquireLock succeeds for new lock")
    void testAcquireLockNew() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        WorkspaceFileLock lock = createLock("/tmp/workspace/file.txt", "holder1", 3600);

        assertTrue(manager.acquireLock(lock));
        assertEquals(lock, manager.getLock("/tmp/workspace/file.txt"));
    }

    @Test
    @DisplayName("Test acquireLock replaces expired lock")
    void testAcquireLockExpired() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        WorkspaceFileLock expiredLock = createExpiredLock("/tmp/workspace/file.txt", "holder1", 1);
        manager.acquireLock(expiredLock);

        WorkspaceFileLock newLock = createLock("/tmp/workspace/file.txt", "holder2", 3600);
        assertTrue(manager.acquireLock(newLock));
        assertEquals("holder2", manager.getLock("/tmp/workspace/file.txt").getHolderId());
    }

    @Test
    @DisplayName("Test acquireLock fails for non-expired lock")
    void testAcquireLockNonExpired() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        WorkspaceFileLock lock = createLock("/tmp/workspace/file.txt", "holder1", 3600);
        manager.acquireLock(lock);

        WorkspaceFileLock newLock = createLock("/tmp/workspace/file.txt", "holder2", 3600);
        assertFalse(manager.acquireLock(newLock));
        assertEquals("holder1", manager.getLock("/tmp/workspace/file.txt").getHolderId());
    }

    @Test
    @DisplayName("Test releaseLock succeeds with correct holderId")
    void testReleaseLockCorrectHolder() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        WorkspaceFileLock lock = createLock("/tmp/workspace/file.txt", "holder1", 3600);
        manager.acquireLock(lock);

        assertTrue(manager.releaseLock("/tmp/workspace/file.txt", "holder1"));
        assertNull(manager.getLock("/tmp/workspace/file.txt"));
    }

    @Test
    @DisplayName("Test releaseLock fails with wrong holderId")
    void testReleaseLockWrongHolder() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        WorkspaceFileLock lock = createLock("/tmp/workspace/file.txt", "holder1", 3600);
        manager.acquireLock(lock);

        assertFalse(manager.releaseLock("/tmp/workspace/file.txt", "holder2"));
        assertNotNull(manager.getLock("/tmp/workspace/file.txt"));
    }

    @Test
    @DisplayName("Test releaseLock fails for non-existent lock")
    void testReleaseLockNonExistent() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        assertFalse(manager.releaseLock("/tmp/workspace/nonexistent.txt", "holder1"));
    }

    @Test
    @DisplayName("Test getLock returns null for non-existent file")
    void testGetLockNonExistent() {
        TeamWorkspaceManager manager = createManager("/tmp/workspace", "test-team");
        assertNull(manager.getLock("/tmp/workspace/nonexistent.txt"));
    }

    // ========== Mount tests ==========

    @Test
    @DisplayName("Test mountIntoWorkspace creates team directory")
    void testMountIntoWorkspaceCreatesDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("workspace-test");
        try {
            TeamWorkspaceManager manager = createManager(tempDir.toString(), "test-team");
            manager.mountIntoWorkspace(tempDir.toString());

            Path teamDir = tempDir.resolve(".team");
            assertTrue(Files.exists(teamDir));
        } finally {
            Files.deleteIfExists(tempDir.resolve(".team").resolve("test-team"));
            Files.deleteIfExists(tempDir.resolve(".team"));
            Files.deleteIfExists(tempDir);
        }
    }
}

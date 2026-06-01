/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_teams.workspace;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamWorkspace models.
 * 
 * <p>Mirrors Python's {@code models.py} module in
 * {@code openjiuwen.agent_teams.team_workspace.models}.
 * Ported from Python: agent-core-0.1.12/openjiuwen/agent_teams/team_workspace/models.py
 * 
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/team_workspace/test_models.py}.
 */
@ExtendWith(MockitoExtension.class)
class TeamWorkspaceModelsTest {

    // ========== TeamWorkspaceConfig tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test TeamWorkspaceConfig default values")
    void testConfigDefaults() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        
        assertFalse(config.isEnabled());
        assertNull(config.getRootPath());
        assertEquals(4, config.getArtifactDirs().size());
        assertTrue(config.isVersionControl());
        assertEquals(ConflictStrategy.LOCK, config.getConflictStrategy());
        assertNull(config.getRemoteUrl());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test TeamWorkspaceConfig setters")
    void testConfigSetters() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        
        config.setEnabled(true);
        assertTrue(config.isEnabled());
        
        config.setRootPath("/tmp/workspace");
        assertEquals("/tmp/workspace", config.getRootPath());
        
        List<String> dirs = List.of("artifacts/test");
        config.setArtifactDirs(dirs);
        assertEquals(1, config.getArtifactDirs().size());
        assertEquals("artifacts/test", config.getArtifactDirs().get(0));
        
        config.setVersionControl(false);
        assertFalse(config.isVersionControl());
        
        config.setConflictStrategy(ConflictStrategy.MERGE);
        assertEquals(ConflictStrategy.MERGE, config.getConflictStrategy());
        
        config.setRemoteUrl("https://github.com/example/repo.git");
        assertEquals("https://github.com/example/repo.git", config.getRemoteUrl());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test TeamWorkspaceConfig artifactDirs returns copy")
    void testConfigArtifactDirsCopy() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        List<String> dirs = config.getArtifactDirs();
        dirs.add("new-dir");
        
        // Original should not be modified
        assertEquals(4, config.getArtifactDirs().size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test TeamWorkspaceConfig setArtifactDirs null")
    void testConfigSetArtifactDirsNull() {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        config.setArtifactDirs(null);
        
        assertTrue(config.getArtifactDirs().isEmpty());
    }

    // ========== WorkspaceMode tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceMode values")
    void testWorkspaceModeValues() {
        assertEquals(2, WorkspaceMode.values().length);
        assertEquals(WorkspaceMode.LOCAL, WorkspaceMode.valueOf("LOCAL"));
        assertEquals(WorkspaceMode.DISTRIBUTED, WorkspaceMode.valueOf("DISTRIBUTED"));
        assertEquals("local", WorkspaceMode.LOCAL.getValue());
        assertEquals("distributed", WorkspaceMode.DISTRIBUTED.toString());
    }

    // ========== ConflictStrategy tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test ConflictStrategy values")
    void testConflictStrategyValues() {
        assertEquals(3, ConflictStrategy.values().length);
        assertEquals(ConflictStrategy.LOCK, ConflictStrategy.valueOf("LOCK"));
        assertEquals(ConflictStrategy.MERGE, ConflictStrategy.valueOf("MERGE"));
        assertEquals(ConflictStrategy.LAST_WRITE_WINS, ConflictStrategy.valueOf("LAST_WRITE_WINS"));
        assertEquals("lock", ConflictStrategy.LOCK.getValue());
        assertEquals("merge", ConflictStrategy.MERGE.toString());
        assertEquals("last_write_wins", ConflictStrategy.LAST_WRITE_WINS.getValue());
    }

    // ========== WorkspaceFileLock tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceFileLock constructor with default timeout")
    void testLockDefaultTimeout() {
        String acquiredAt = Instant.now().toString();
        WorkspaceFileLock lock = new WorkspaceFileLock(
            "/tmp/file.txt", "holder1", "holder-name", acquiredAt
        );
        
        assertEquals("/tmp/file.txt", lock.getFilePath());
        assertEquals("holder1", lock.getHolderId());
        assertEquals("holder-name", lock.getHolderName());
        assertEquals(acquiredAt, lock.getAcquiredAt());
        assertEquals(300, lock.getTimeoutSeconds()); // Default 5 minutes
    }

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceFileLock constructor with custom timeout")
    void testLockCustomTimeout() {
        String acquiredAt = Instant.now().toString();
        WorkspaceFileLock lock = new WorkspaceFileLock(
            "/tmp/file.txt", "holder1", "holder-name", acquiredAt, 600
        );
        
        assertEquals(600, lock.getTimeoutSeconds());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceFileLock isExpired returns false for new lock")
    void testLockNotExpired() {
        String acquiredAt = Instant.now().toString();
        WorkspaceFileLock lock = new WorkspaceFileLock(
            "/tmp/file.txt", "holder1", "holder-name", acquiredAt, 3600
        );
        
        assertFalse(lock.isExpired());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceFileLock isExpired returns true for old lock")
    void testLockExpired() {
        // Create a lock acquired 1 hour ago with 1 minute timeout
        String acquiredAt = Instant.now().minusSeconds(3600).toString();
        WorkspaceFileLock lock = new WorkspaceFileLock(
            "/tmp/file.txt", "holder1", "holder-name", acquiredAt, 60
        );
        
        assertTrue(lock.isExpired());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test WorkspaceFileLock parses Python offset timestamp")
    void testLockParsesPythonOffsetTimestamp() {
        String acquiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(3600).toString();
        WorkspaceFileLock lock = new WorkspaceFileLock(
            "/tmp/file.txt", "holder1", "holder-name", acquiredAt, 60
        );

        assertTrue(lock.isExpired());
    }
}

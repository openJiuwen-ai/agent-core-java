
package com.openjiuwen.agentteams.teamworkspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

class TeamWorkspaceCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void workspaceConfigShouldExposeExpectedDefaults() {
        TeamWorkspaceConfig config = TeamWorkspaceConfig.builder().build();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getArtifactDirs()).contains("artifacts/code", "artifacts/docs", "artifacts/reports",
                "trajectories");
        assertThat(config.getConflictStrategy()).isEqualTo(ConflictStrategy.LOCK);
    }

    @Test
    void workspaceFileLockShouldComputeExpiry() {
        WorkspaceFileLock fresh = WorkspaceFileLock.builder().filePath("a.txt").holderId("m1").holderName("alice")
                .acquiredAt(OffsetDateTime.now(ZoneOffset.UTC).toString()).timeoutSeconds(600).build();
        WorkspaceFileLock expired = WorkspaceFileLock.builder().filePath("a.txt").holderId("m1").holderName("alice")
                .acquiredAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(301).toString()).timeoutSeconds(300)
                .build();
        assertThat(fresh.isExpired()).isFalse();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test
    void managerShouldInitializeWorkspaceAndArtifactDirs() throws Exception {
        Path workspace = tempDir.resolve("shared-workspace");
        TeamWorkspaceManager manager =
            new TeamWorkspaceManager(TeamWorkspaceConfig.builder().build(), workspace.toString(), "team-alpha");

        manager.initialize();

        assertThat(Files.isDirectory(workspace)).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("skills"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("artifacts/code"))).isTrue();
    }

    @Test
    void managerShouldMountIntoWorkspaceAndManageLocks() throws Exception {
        Path workspace = tempDir.resolve("shared-workspace");
        Files.createDirectories(workspace);
        TeamWorkspaceManager manager =
            new TeamWorkspaceManager(TeamWorkspaceConfig.builder().build(), workspace.toString(), "team-alpha");
        Path agentWorkspace = tempDir.resolve("agent-workspace");
        Files.createDirectories(agentWorkspace);

        manager.mountIntoWorkspace(agentWorkspace.toString());

        assertThat(Files.exists(agentWorkspace.resolve(".team").resolve("team-alpha"))).isTrue();
        assertThat(manager.acquireLock("src/Main.java", "m1", "alice", 300)).isTrue();
        assertThat(manager.acquireLock("src/Main.java", "m2", "bob", 300)).isFalse();
        assertThat(manager.releaseLock("src/Main.java", "m1")).isTrue();
    }

    @Test
    void managerShouldHandleLeaderSideDistributedLockRequestsLikePython() throws Exception {
        Path workspace = tempDir.resolve("distributed-workspace");
        Files.createDirectories(workspace);
        TeamWorkspaceManager manager = new TeamWorkspaceManager(TeamWorkspaceConfig.builder().build(),
                workspace.toString(), "team-alpha", WorkspaceMode.DISTRIBUTED);

        WorkspaceLockResponse first = manager.handleLockRequest(
                WorkspaceLockRequest.builder().teamName("team-alpha").memberName("m1").holderName("Alice")
                        .action("acquire").filePath("artifacts/code/app.java").timeoutSeconds(120).build());
        WorkspaceLockResponse blocked = manager.handleLockRequest(
                WorkspaceLockRequest.builder().teamName("team-alpha").memberName("m2").holderName("Bob")
                        .action("acquire").filePath("artifacts/code/app.java").timeoutSeconds(120).build());
        WorkspaceLockResponse wrongRelease = manager.handleLockRequest(WorkspaceLockRequest.builder()
                .teamName("team-alpha").memberName("m2").action("release").filePath("artifacts/code/app.java").build());
        WorkspaceLockResponse released = manager.handleLockRequest(WorkspaceLockRequest.builder().teamName("team-alpha")
                .memberName("m1").action("release").filePath("artifacts/code/app.java").build());

        assertThat(first.isGranted()).isTrue();
        assertThat(blocked.isGranted()).isFalse();
        assertThat(blocked.getHolder()).containsEntry("holder_id", "m1").containsEntry("holder_name", "Alice");
        assertThat(wrongRelease.isGranted()).isFalse();
        assertThat(wrongRelease.getHolder()).containsEntry("holder_id", "m1");
        assertThat(released.isGranted()).isTrue();
        assertThat(manager.getLock("artifacts/code/app.java")).isNull();
    }
}

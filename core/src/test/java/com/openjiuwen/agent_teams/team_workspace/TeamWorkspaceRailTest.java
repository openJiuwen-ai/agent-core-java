/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceArtifactEvent;
import com.openjiuwen.core.sysop.Cwd;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamWorkspaceRail}.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceRail} in
 * {@code openjiuwen/agent_teams/team_workspace/rails.py}.</p>
 */
class TeamWorkspaceRailTest {

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void initStoresTeamWorkspacePathInCwdState() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager(workspace, WorkspaceMode.LOCAL, false, null), "member-a");

        rail.init(new Object());

        assertThat(Cwd.getTeamWorkspace()).isEqualTo(workspace.toAbsolutePath().normalize().toString());
    }

    @Test
    void readAndWriteTeamPathsPullOnlyWhenDistributedAndThrottleAllows() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        AtomicReference<Double> now = new AtomicReference<>(10.0D);
        RecordingWorkspaceManager manager = manager(workspace, WorkspaceMode.DISTRIBUTED, false, null);
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager, "member-a", now::get);

        rail.beforeToolCall("read_file", Map.of("file_path", ".team/team-alpha/report.md"), null)
                .toCompletableFuture().join();
        now.set(12.0D);
        rail.beforeToolCall("grep", Map.of("file_path", ".team/team-alpha/report.md"), null)
                .toCompletableFuture().join();
        now.set(16.0D);
        rail.beforeToolCall("write_file", Map.of("file_path", ".team/team-alpha/report.md"), new LinkedHashMap<>())
                .toCompletableFuture().join();

        assertThat(manager.pullCount).hasValue(2);
    }

    @Test
    void beforeWriteRecordsRejectionWhenForeignActiveLockExists() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        RecordingWorkspaceManager manager = manager(workspace, WorkspaceMode.LOCAL, false, null);
        WorkspaceFileLock lock = new WorkspaceFileLock(
                ".team/team-alpha/artifacts/report.md",
                "member-b",
                "Member B",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                300
        );
        manager.setLock(lock);
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager, "member-a");
        Map<String, Object> extra = new LinkedHashMap<>();

        rail.beforeToolCall("edit_file", Map.of("file_path", ".team/team-alpha/artifacts/report.md"), extra)
                .toCompletableFuture().join();

        assertThat(extra)
                .containsEntry("workspace_lock_rejected",
                        "File '.team/team-alpha/artifacts/report.md' is locked by Member B (member-b)");
    }

    @Test
    void beforeWriteIgnoresNonTeamPathAndOwnLock() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        RecordingWorkspaceManager manager = manager(workspace, WorkspaceMode.LOCAL, false, null);
        WorkspaceFileLock lock = new WorkspaceFileLock(
                ".team/team-alpha/artifacts/report.md",
                "member-a",
                "Member A",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                300
        );
        manager.setLock(lock);
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager, "member-a");
        Map<String, Object> extra = new LinkedHashMap<>();

        rail.beforeToolCall("write_file", Map.of("file_path", "outside/report.md"), extra)
                .toCompletableFuture().join();
        rail.beforeToolCall("write_file", Map.of("file_path", ".team/team-alpha/artifacts/report.md"), extra)
                .toCompletableFuture().join();

        assertThat(extra).doesNotContainKey("workspace_lock_rejected");
    }

    @Test
    void afterWriteCommitsResolvedHubPathAndPublishesArtifactEvent() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        AtomicReference<String> topic = new AtomicReference<>();
        AtomicReference<BaseEventMessage> event = new AtomicReference<>();
        RecordingWorkspaceManager manager = manager(workspace, WorkspaceMode.LOCAL, true, (eventTopic, payload) -> {
            topic.set(eventTopic);
            event.set(payload);
            return CompletableFuture.completedFuture(null);
        });
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager, "member-a");

        rail.afterToolCall("write_file", Map.of("file_path", ".team/team-alpha/artifacts/report.md"))
                .toCompletableFuture().join();

        assertThat(manager.lastAutoCommitPath).hasValue("artifacts/report.md");
        assertThat(manager.lastAutoCommitMember).hasValue("member-a");
        assertThat(topic).hasValue(TeamEvent.WORKSPACE_ARTIFACT_UPDATED);
        WorkspaceArtifactEvent artifact = (WorkspaceArtifactEvent) event.get();
        assertThat(artifact.getTeamName()).isEqualTo("team-alpha");
        assertThat(artifact.getMemberName()).isEqualTo("member-a");
        assertThat(artifact.getArtifactPath()).isEqualTo("artifacts/report.md");
    }

    @Test
    void afterWritePublishesLegacyTeamPathWhenVersionControlDisabled() throws Exception {
        Path workspace = Files.createTempDirectory("team-workspace-rail");
        AtomicReference<BaseEventMessage> event = new AtomicReference<>();
        RecordingWorkspaceManager manager = manager(workspace, WorkspaceMode.LOCAL, false, (eventTopic, payload) -> {
            event.set(payload);
            return CompletableFuture.completedFuture(null);
        });
        TeamWorkspaceRail rail = new TeamWorkspaceRail(manager, "member-a");

        rail.afterToolCall("edit_file", Map.of("file_path", ".team/artifacts/report.md"))
                .toCompletableFuture().join();

        assertThat(manager.autoCommitCount).hasValue(0);
        assertThat(((WorkspaceArtifactEvent) event.get()).getArtifactPath()).isEqualTo("artifacts/report.md");
    }

    private static RecordingWorkspaceManager manager(
            Path workspace,
            WorkspaceMode mode,
            boolean versionControl,
            TeamWorkspaceManager.PublishEventCallback publishEvent
    ) {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        config.setVersionControl(versionControl);
        config.setConflictStrategy(ConflictStrategy.LOCK);
        return new RecordingWorkspaceManager(config, workspace.toString(), "team-alpha", mode, publishEvent);
    }

    /**
     * Test double for the workspace manager calls made by the rail.
     *
     * <p>Mirrors Python's {@code TeamWorkspaceManager} collaborator in
     * {@code openjiuwen/agent_teams/team_workspace/rails.py}.</p>
     */
    private static final class RecordingWorkspaceManager extends TeamWorkspaceManager {
        private final AtomicInteger pullCount = new AtomicInteger();
        private final AtomicInteger autoCommitCount = new AtomicInteger();
        private final AtomicReference<String> lastAutoCommitPath = new AtomicReference<>();
        private final AtomicReference<String> lastAutoCommitMember = new AtomicReference<>();
        private final Map<String, WorkspaceFileLock> locks = new LinkedHashMap<>();

        private RecordingWorkspaceManager(
                TeamWorkspaceConfig config,
                String workspacePath,
                String teamName,
                WorkspaceMode mode,
                PublishEventCallback publishEvent
        ) {
            super(config, workspacePath, teamName, mode, null, null, null, publishEvent);
        }

        private void setLock(WorkspaceFileLock lock) {
            locks.put(lock.getFilePath(), lock);
        }

        @Override
        public CompletableFuture<Boolean> pull() {
            pullCount.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public WorkspaceFileLock getLock(String filePath) {
            return locks.get(filePath);
        }

        @Override
        public CompletableFuture<String> autoCommit(String relativePath, String memberName) {
            autoCommitCount.incrementAndGet();
            lastAutoCommitPath.set(relativePath);
            lastAutoCommitMember.set(memberName);
            return CompletableFuture.completedFuture("commit-sha");
        }
    }
}
